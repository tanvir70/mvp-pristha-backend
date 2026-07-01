# Engineering Log — Enterprise Auth Hardening (Social Login, MFA, Sessions, Audit, Rate Limiting, Password Recovery)

**Author:** Tanvir
**Date:** 2026-07-01
**Branch:** `feature/jwt-login-logout`
**Trigger:** The existing JWT login/refresh/logout implementation (see
`doc/2026-06-23-jwt-login-logout-implementation.md`) was an MVP-scoped slice
of ID-FR-03/04/05 and NFR-04. The request for this session was explicit:
*"do not just code a mvp... a enterprise security login logout system"* —
i.e. build out the full authentication surface a production consumer app
would need: Google social login, optional MFA, session/device visibility and
revocation, a security audit trail, endpoint rate limiting, and self-service
password recovery/change — on top of the existing RS256 JWT + opaque
Redis-backed refresh token foundation, without discarding or redesigning it.

---

## 0. Scope-setting (asked before writing code)

"Enterprise security" is not a fixed checklist, so before touching any file
four scoping questions were put to the user:

1. **Base branch** — the JWT work already existed, unmerged, on
   `feature/jwt-login-logout`. Chosen: **build on top of that branch**
   (not merge to `develop` first, not start over). This kept the diff a
   continuation of one coherent auth story instead of two overlapping ones.
2. **Social providers** — chosen: **Google only**. Facebook/Apple were
   explicitly deferred; the verification abstraction (`GoogleTokenVerifierService`)
   is provider-shaped so adding another provider later is a new
   implementation of a similar interface, not a redesign.
3. **Hardening features** — chosen (all four): OTP-based MFA, session/device
   management, security audit log, rate limiting on auth endpoints.
4. **Password lifecycle** — chosen: **include** forgot/reset/change password
   in this same build rather than defer it, since MFA and password-reset both
   need the same OTP delivery mechanism — building it twice would have been
   worse than building it once and sharing it.

This is why the OTP mechanism ended up as shared infrastructure (see §2)
rather than three separate implementations.

---

## 1. Research before writing (Explore agent)

Before any code was written, a research pass mapped the *actual* current
state of the `identity` module (not assumed state), specifically:

- The `User` entity had **no** MFA flag, no social-provider link, and both
  `phone` and `password_hash` were `NOT NULL` — a hard blocker for social-only
  accounts that don't have a phone number from Google.
- OTP "verification" was **entirely fake**: `IdentityServiceImpl.verifyOtp`
  hardcoded the string `"123456"` with no generation, storage, expiry, or
  delivery of any kind.
- No SMS/notification gateway existed anywhere in the codebase.
- No session/device tracking, no audit logging, no rate limiting existed —
  confirmed via targeted `grep` across the whole repo, not just `identity`.
- The coding guideline's module-encapsulation rules (`api/contract` +
  `api/dto` are the only public surface; everything under `internal/` is
  package-private; DTOs never expose entities) were read in full and applied
  to every new class.
- **The branch does not currently compile**, independent of this work:
  `AuthorRequestRepository`, `AdminAuthorServiceImpl`, `AuthorProfileServiceImpl`,
  `AuthorRequestServiceImpl` reference `AuthorRequest`/`UserRole`/
  `AuthorRequestStatus` types that don't exist in `identity.internal.entity`,
  and several `catalog` repositories/services reference a `Post`/`Tag`/
  `PostMedia` entity model that isn't present either. Cross-referencing the
  most recent `doc/log` entry, `UserRole` was **deliberately deleted** in an
  earlier DB-design migration (roles are derived from `AuthorProfile`
  existence, never stored) and the admin-gated `AuthorRequest` workflow was
  **deliberately removed** in favor of open author onboarding — but the
  service/repository files that still reference them were never deleted from
  this branch. This is pre-existing and unrelated to auth; it was **not**
  touched, only confirmed (via `compileJava` output, cross-checked file by
  file) that none of the errors trace back to any file this session created
  or modified.

---

## 2. OTP: made real, and made shared

**Decision: one `OtpService`, used by signup verification, login MFA, and
password reset — not three separate mechanisms.**

- `OtpServiceImpl` generates a random 6-digit code (`SecureRandom`), hashes it
  with the **existing** `PasswordEncoder` (BCrypt) bean before storing it, and
  stores it in Redis via new `CacheService` methods (`storeOtp`/`getOtpHash`/
  `deleteOtp`/`incrementOtpAttempts`), keyed `otp:{purpose}:{phone}`.
  - *Why BCrypt for a 6-digit code, when BCrypt is meant for passwords?*
    Defense-in-depth against a Redis compromise, not against online guessing —
    online guessing is already bounded by `incrementOtpAttempts` (deletes the
    OTP outright after `identity.auth.otp-max-attempts` wrong tries, forcing a
    fresh request) and by the endpoint-level rate limiter (§6). Reusing the
    already-injected `PasswordEncoder` bean avoided adding a second hashing
    scheme for no real gain (ladder rung 4: reuse an already-installed
    dependency).
  - *Why purpose-scoped keys instead of one OTP per phone?* A user requesting
    a password-reset code shouldn't be able to reuse it to pass an MFA
    challenge, or vice versa. `OtpPurpose` (`SIGNUP_VERIFICATION`,
    `LOGIN_MFA`, `PASSWORD_RESET`) keeps the three flows cryptographically
    independent even though they share one code path.
- `OtpDeliveryService` is a one-method interface (`send(phone, code)`).
  `LoggingOtpDeliveryServiceImpl` is the only implementation: it **logs** the
  code instead of sending an SMS. **This is a known, explicitly-marked
  shortcut** (`ponytail:` comment in the source) — there is no SMS gateway
  (Twilio/SNS/local aggregator) wired into this project yet, and adding one
  would require credentials/config this session doesn't have. Swapping in a
  real gateway later means writing one new class against this interface; no
  other code changes.
- `IdentityServiceImpl.signUp`/`verifyOtp` were **rewired** off the hardcoded
  `"123456"` stub onto this same `OtpService`. This was in scope because
  leaving the old fake OTP alongside a new real one would have meant two
  competing, inconsistent "OTP" concepts in the same module.

---

## 3. Google social login

**Decision: verify a client-obtained Google ID token server-side, not a
full server-side OAuth2 authorization-code redirect flow.**

This project is an API backend for mobile/SPA clients, not a server-rendered
app — the standard pattern for that shape (used by Firebase Auth, Auth0,
etc.) is: the client does Google Sign-In natively, gets an ID token from
Google, and hands *that* to the backend, which verifies its signature and
claims. A full authorization-code redirect flow is built for server-rendered
apps with a browser session and would add a redirect/callback surface this
API doesn't have a use for.

- Added `com.google.api-client:google-api-client:2.9.0` (verified as the
  current version via Maven Central before pinning it) — Google's own
  `GoogleIdTokenVerifier` handles JWKS fetch/cache/rotation and clock-skew
  tolerance correctly; hand-rolling that against `NimbusJwtDecoder` would have
  meant re-implementing a well-known, security-sensitive piece of logic that
  Google already maintains.
- `GoogleAuthConfig` builds the `GoogleIdTokenVerifier` bean with the
  configured OAuth client ID as the required audience
  (`identity.auth.google.client-id`, sourced from `GOOGLE_OAUTH_CLIENT_ID`).
  **This env var is currently unset/empty** — social login will reject every
  token until a real Google Cloud OAuth client ID is configured. This is
  expected; wiring that up is an infrastructure/ops step, not a code change.
- `GoogleTokenVerifierServiceImpl` wraps the verifier and throws
  `AuthenticationFailedException` uniformly for a bad signature, wrong
  audience, expired token, or any I/O failure — the caller never needs to
  know which.
- `User` schema changes (migration `V2`, see §7): `phone` and `password_hash`
  became **nullable**, and a new **nullable, unique** `google_sub` column was
  added.
  - *Why not a separate `SocialIdentity` table?* A user has at most one
    Google identity in this design (no "link multiple Google accounts"
    requirement was in scope), so a single nullable column is the smaller,
    equally-correct model. A join table would be justified the day a second
    provider needs linking to the *same* row — not before.
  - *Why nullable phone/password instead of forcing a phone-collection step
    after Google sign-in?* Forcing phone collection would mean a two-step
    signup for social users just to satisfy a schema constraint that had no
    other reason to exist. Email (which Google always provides and already
    had a partial-unique index in the schema) becomes the natural identity
    key for social-only accounts instead.
- Login flow: `loginWithGoogle` looks up by `google_sub` first, falls back to
  linking-by-`email` (so a user who originally signed up with phone+password
  and later uses "Sign in with Google" with the same email gets **linked**,
  not duplicated), and only creates a brand-new `User` row if neither matches.
  A social login goes through the exact same `completeAuthentication` →
  `issueTokenResponse` path as a password login, including the same MFA gate
  (see §4) and the same session-row creation (see §5).

---

## 4. MFA (OTP second factor)

**Decision: MFA is a gate inserted between "credentials verified" and "tokens
issued," not a separate parallel login endpoint.**

- `User.mfaEnabled` (new column, default `false`). Enable/disable requires
  re-entering the account password (`MfaToggleRequestDto{password}`) — a
  sensitive account-security toggle shouldn't be changeable just because the
  caller holds a still-valid 15-minute access token.
- When `login()` or `loginWithGoogle()` succeeds against the primary factor
  and `user.isMfaEnabled()` is true, the shared `completeAuthentication`
  method does **not** issue tokens. Instead it:
  1. Sends a `LOGIN_MFA`-purpose OTP to the user's phone via `OtpService`.
  2. Generates a random opaque `mfaToken` (UUID) and stores
     `mfaToken → userId` in Redis with a short TTL
     (`identity.auth.mfa-challenge-ttl`, default 5m).
  3. Returns `AuthTokenResponseDto` with **only** `mfaRequired=true` and
     `mfaToken` populated — `accessToken`/`refreshToken`/etc. are left null.
  - *Why extend the existing `AuthTokenResponseDto` with two nullable fields
    instead of a new response type?* A new type would force every client to
    branch on response **shape**; a nullable-field discriminator
    (`mfaRequired`) lets clients branch on one boolean while the DTO stays a
    single, stable contract. The five pre-existing tests for plain
    login/refresh/logout are unaffected because `mfaEnabled` defaults to
    `false` — they never touch this branch.
- `POST /mfa/verify {mfaToken, code}` resolves `mfaToken → userId` from Redis,
  verifies the OTP against `OtpPurpose.LOGIN_MFA` for that user's phone, and
  on success calls the **same** `issueTokenResponse` used by normal login —
  there is exactly one place in the codebase that mints tokens.
- **Known limitation (documented, not fixed):** a user with no password set
  (a Google-only account) cannot currently call `enableMfa`/`disableMfa` or
  `changePassword`, because those all confirm identity via
  `requireUserWithPassword`. Fixing this properly means accepting a fresh
  Google ID token as the confirmation factor for social-only accounts instead
  of a password — flagged with a `ponytail:` comment at the point where it
  would need to change, not silently left as a mystery 403.

---

## 5. Session & device management

**Decision: Redis remains the source of truth for "is this refresh token
currently valid," but a new Postgres `user_sessions` table exists purely for
human-readable listing and targeted revocation.**

- Every *new* login (password, social, or MFA-completed) inserts a row:
  `user_id`, `refresh_token_id` (the same opaque UUID stored in Redis),
  `device_label`/`user_agent` (from the `User-Agent` header), `ip_address`
  (from `HttpServletRequest.getRemoteAddr()`), `last_used_at`.
- **Critical design point:** a token **refresh** does *not* insert a new row
  — it **updates the existing session row's** `refresh_token_id` and
  `last_used_at`. Access tokens live 15 minutes and refreshes happen
  continuously while a client is active; if every refresh created a new
  session row, "My active sessions" would fill up with dozens of rows per
  device per day, making the feature useless. `AuthServiceImpl.refreshToken`
  looks up the session by `(userId, oldTokenId)` before rotating and passes
  it into `issueTokenResponse` so the row is updated in place, not duplicated.
- `GET /sessions`, `DELETE /sessions/{id}` (revoke one), `DELETE /sessions`
  (revoke all — "log out everywhere") were added. Revoking a session both
  flags the Postgres row (`revoked=true`) **and** deletes the corresponding
  Redis refresh-token key, so a revoked session can't be used to refresh even
  though the JWT access token technically still has a few minutes left on it
  — this is the same access-token-revocation waiver already accepted by the
  original SRS-driven design (§ the earlier 2026-06-23 doc), applied
  consistently here rather than re-litigated.
- `revokeAllSessions` (and, by extension, password reset/change — see §6)
  needed to delete *every* refresh token for a user from Redis in one call.
  `CacheService.deleteAllRefreshTokens` does this with a `KEYS
  refresh:{userId}:*` scan-and-delete. **Marked as a `ponytail:` shortcut**:
  `KEYS` is O(n) over the whole Redis keyspace, which is fine at this
  project's scale but would need to become a per-user Redis `SET` of live
  token IDs (`SADD` on issue, `SMEMBERS`+`DEL` here) if the refresh-token
  keyspace ever grows large enough for `KEYS` to become a real latency
  concern.

---

## 6. Password reset & change

- `POST /password/forgot {phone}` sends a `PASSWORD_RESET`-purpose OTP —
  **but only if the phone is actually registered**, while still returning the
  same response either way. This is a deliberate, commented decision: an
  endpoint that responds differently for "phone exists" vs. "phone doesn't
  exist" is a user-enumeration side channel; this one behaves identically in
  both cases so it can't be used to discover which phone numbers have
  accounts.
- `POST /password/reset {phone, code, newPassword}` verifies the OTP,
  re-hashes the password, and — importantly — **revokes every existing
  session and refresh token for that user** (a stolen-password scenario means
  every existing session should die, not just future ones).
- `POST /password/change {oldPassword, newPassword}` (authenticated) does the
  same revoke-everything step after a successful change, on the same
  reasoning: if a password changes, anything authenticated with the *old*
  password (including the caller's own still-live refresh token) should stop
  working, forcing a clean re-login. The currently-in-flight access token is
  still technically valid for its remaining ≤15 minutes — again, the
  documented access-token-revocation waiver, not an oversight.

---

## 7. Security audit log

- New `security_audit_logs` table + `SecurityAuditLog` entity: `user_id`
  (nullable — a failed login before the phone resolves to a user still gets
  logged), `phone`, `event_type`, `ip_address`, `user_agent`, `detail`,
  `created_at`.
- `SecurityEventType` enum covers the full lifecycle: login success/failure,
  account-locked, logout, token-refresh, social login, MFA
  enabled/disabled/challenge/success/failure, password-reset
  requested/completed, password-changed, session-revoked,
  all-sessions-revoked.
- **The one correctness detail that would have silently broken this
  feature:** `AuthServiceImpl` is `@Transactional` at the class level, and a
  *failed* login (for example) throws `AuthenticationFailedException` **after**
  the audit-log write. If the audit write shared the caller's transaction, the
  exception would roll back the whole transaction — **including the audit log
  entry it was trying to record**, which defeats the entire point of auditing
  failures. `SecurityAuditServiceImpl.record(...)` is annotated
  `@Transactional(propagation = Propagation.REQUIRES_NEW)` specifically so it
  commits independently of whatever the caller's transaction eventually does.
- `GET /security-log` (authenticated) exposes the caller's own most recent 50
  events — self-service visibility, not an admin panel (no admin/other-users
  view was in scope for this session).

---

## 8. Rate limiting on auth endpoints

**Decision: a small Redis-backed counter (the same `INCR`+`EXPIRE` pattern
already used for login lockout) behind a custom filter, not a new dependency
like Bucket4j.** The existing `CacheService.incrementLoginAttempts` already
proved this exact pattern works; generalizing it to
`incrementRateLimit(bucketKey, window)` reused an already-installed
dependency (`StringRedisTemplate`) instead of adding a library for something
a few lines already do.

- `AuthRateLimitFilter` (a `OncePerRequestFilter`) only acts on requests
  under `/api/v1/auth/**`; every other request passes straight through. It
  buckets by `{remote IP}:{URI}` and returns HTTP 429 (RFC 7807 problem-detail
  body) once `identity.auth.rate-limit-max-requests` is exceeded inside
  `identity.auth.rate-limit-window` (defaults: 20 requests / 1 minute).
- **A specific, non-obvious Spring Boot/Security gotcha had to be handled
  correctly:** any `Filter` bean in the application context gets
  auto-registered by Spring Boot as a *second*, unscoped servlet filter
  (applied to `/*`) **in addition to** whatever the Spring Security filter
  chain does with it. Wiring `AuthRateLimitFilter` into
  `HttpSecurity.addFilterBefore(...)` without addressing this would have run
  the rate-limit check **twice** per request. `SecurityConfig` explicitly
  registers a `FilterRegistrationBean<AuthRateLimitFilter>` with
  `setEnabled(false)` to suppress Boot's automatic registration, so the
  filter runs exactly once, in the position chosen inside the security chain
  (`addFilterBefore(rateLimitFilter, BasicAuthenticationFilter.class)`).

---

## 9. Route & security-config changes

- `IdentityRouteConstant` gained: `SOCIAL_GOOGLE`, `MFA_VERIFY`,
  `MFA_ENABLE`, `MFA_DISABLE`, `PASSWORD_FORGOT`, `PASSWORD_RESET`,
  `PASSWORD_CHANGE`, `SESSIONS`, `SECURITY_LOG` — continuing the
  route-constant convention already established for this module (no raw
  string literals in `@RequestMapping`/`@PostMapping` anywhere in these
  controllers).
- **`SecurityConfig`'s authorization rule changed from a blanket `permitAll`
  on `/api/v1/auth/**` to an explicit allow-list.** The old rule was correct
  when every route under that prefix was a pre-authentication endpoint
  (login/refresh/logout/signup/verify-otp). It is **no longer correct** now
  that account-management endpoints (`mfa/enable`, `mfa/disable`,
  `password/change`, `sessions`, `security-log`) also live under that same
  prefix and genuinely require a valid JWT. The new config explicitly
  `permitAll`s only the pre-auth set (signup, verify-otp, login, refresh,
  logout, social/google, mfa/verify, password/forgot, password/reset); every
  other route — including the rest of `/api/v1/auth/**` — falls through to
  `anyRequest().authenticated()`.
- Two controllers now exist under the same base path:
  `AuthController` (the pre-auth handshake: login, social login, MFA verify,
  refresh, logout) and the new `AccountSecurityController` (everything that
  assumes an already-authenticated principal: MFA toggles, password
  change, sessions, security log). This mirrors how the endpoints are
  actually gated in `SecurityConfig` and keeps neither controller sprawling
  past what it's responsible for.

---

## 10. Full list of files touched

**New files:**
- `src/main/resources/db/migration/identity/V2__auth_enterprise_hardening.sql`
- `internal/enums/OtpPurpose.java`, `internal/enums/SecurityEventType.java`
- `internal/entity/UserSession.java`, `internal/entity/SecurityAuditLog.java`
- `internal/repository/UserSessionRepository.java`,
  `internal/repository/SecurityAuditLogRepository.java`
- `internal/config/GoogleAuthProperties.java`, `internal/config/GoogleAuthConfig.java`
- `internal/service/OtpDeliveryService.java`,
  `internal/service/LoggingOtpDeliveryServiceImpl.java`,
  `internal/service/OtpService.java`, `internal/service/OtpServiceImpl.java`
- `internal/service/GoogleUserInfo.java`,
  `internal/service/GoogleTokenVerifierService.java`,
  `internal/service/GoogleTokenVerifierServiceImpl.java`
- `internal/service/SecurityAuditService.java`,
  `internal/service/SecurityAuditServiceImpl.java`
- `internal/service/AuthRateLimitFilter.java`
- `api/dto/request/SocialLoginRequestDto.java`, `MfaVerifyRequestDto.java`,
  `MfaToggleRequestDto.java`, `ForgotPasswordRequestDto.java`,
  `ResetPasswordRequestDto.java`, `ChangePasswordRequestDto.java`
- `api/dto/response/SessionResponseDto.java`,
  `api/dto/response/SecurityAuditLogResponseDto.java`
- `api/controller/AccountSecurityController.java`

**Modified files:**
- `internal/entity/User.java` — `phone`/`password_hash` nullable,
  added `googleSub`, `mfaEnabled`.
- `internal/repository/UserRepository.java` — added `findByEmail`,
  `findByGoogleSub`.
- `internal/service/CacheService.java` /
  `internal/service/RedisCacheServiceImpl.java` — added OTP storage, MFA
  challenge storage, rate-limit counters, bulk refresh-token deletion.
- `internal/config/AuthProperties.java` — added `otpTtl`, `otpMaxAttempts`,
  `mfaChallengeTtl`, `rateLimitWindow`, `rateLimitMaxRequests`.
- `internal/config/SecurityConfig.java` — granular route permissions,
  rate-limit filter wiring (see §8/§9).
- `internal/service/AuthServiceImpl.java` — extended with
  `loginWithGoogle`, `verifyMfa`, `enableMfa`/`disableMfa`, session
  listing/revocation, password forgot/reset/change, security-log listing;
  the MFA gate; session-row bookkeeping on login/refresh/logout/revoke.
- `internal/service/IdentityServiceImpl.java` — signup/verify-otp now use
  the real `OtpService` instead of the hardcoded `"123456"` stub.
- `api/contract/AuthService.java` — contract extended with all of the above.
- `api/controller/AuthController.java` — added social-login and MFA-verify
  endpoints.
- `internal/util/constant/IdentityRouteConstant.java` — new route constants.
- `api/dto/response/AuthTokenResponseDto.java` — added `mfaRequired`/
  `mfaToken` fields.
- `build.gradle` — added `com.google.api-client:google-api-client:2.9.0`.
- `application.properties` — OTP TTL/attempts, MFA challenge TTL, rate-limit
  window/threshold, Google OAuth client ID property.
- `src/test/java/.../identity/AuthServiceIntegrationTests.java` — **moved**
  from package `com.prishtha.mvp.identity` into
  `com.prishtha.mvp.identity.internal.service` (via `git mv`), because the
  new tests need direct access to package-private `OtpDeliveryService` (to
  capture generated OTP codes instead of a hardcoded one) and
  `GoogleTokenVerifierService` (to stub Google's response with
  `@MockitoBean` rather than calling the real Google API in a test). Extended
  with tests for MFA challenge/verify, session list/revoke, password
  change/reset, and Google login.

---

## 11. New API surface

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/signup` | public | unchanged |
| POST | `/api/v1/auth/verify-otp` | public | now backed by real OTP |
| POST | `/api/v1/auth/login` | public | may now return an MFA challenge instead of tokens |
| POST | `/api/v1/auth/social/google` | public | Google ID-token login/signup |
| POST | `/api/v1/auth/mfa/verify` | public (holds a challenge token) | completes an MFA login |
| POST | `/api/v1/auth/refresh` | public (holds a refresh token) | unchanged, now also rotates the session row |
| POST | `/api/v1/auth/logout` | public (holds a refresh token) | unchanged, now also revokes the session row |
| POST | `/api/v1/auth/mfa/enable` | **JWT** | requires password confirmation |
| POST | `/api/v1/auth/mfa/disable` | **JWT** | requires password confirmation |
| POST | `/api/v1/auth/password/forgot` | public | enumeration-safe |
| POST | `/api/v1/auth/password/reset` | public (holds an OTP) | revokes all sessions |
| POST | `/api/v1/auth/password/change` | **JWT** | revokes all sessions |
| GET | `/api/v1/auth/sessions` | **JWT** | list own active sessions |
| DELETE | `/api/v1/auth/sessions/{id}` | **JWT** | revoke one session |
| DELETE | `/api/v1/auth/sessions` | **JWT** | revoke all sessions |
| GET | `/api/v1/auth/security-log` | **JWT** | own last 50 audit events |

All of the above (except the pre-existing signup/verify-otp/login/refresh/logout)
are new in this session.

---

## 12. Verification performed

- `./gradlew compileJava` was run and its output filtered for every file
  touched in this session — **zero errors trace to any new or modified
  file**. All remaining errors are the pre-existing, unrelated
  `AuthorRequest`/`UserRole`/catalog-entity gaps described in §1, confirmed
  present *before* this session's changes and left untouched.
- Docker was **not running** in this environment (`docker ps` fails — no
  daemon socket), so Postgres/Redis aren't available and the integration
  test suite could not actually be executed this session. The tests were
  still written (ponytail's rule: non-trivial logic leaves a runnable check
  behind) so they're ready to run the moment the dependencies are up:
  `docker compose up -d` then `./gradlew test`.

---

## 13. Known limitations / deliberate shortcuts (all `ponytail:`-commented in code)

1. **OTP delivery is logged, not sent.** No SMS gateway is wired up. Swap
   `LoggingOtpDeliveryServiceImpl` for a real implementation of
   `OtpDeliveryService` when a provider (Twilio/SNS/local) is chosen.
2. **Google OAuth client ID is unset.** `identity.auth.google.client-id`
   reads from `GOOGLE_OAUTH_CLIENT_ID`, currently empty — social login will
   reject all tokens until this is configured in the deployment environment.
3. **`deleteAllRefreshTokens` uses Redis `KEYS`**, O(n) over the whole
   keyspace. Fine at current scale; upgrade to a per-user Redis `SET` of live
   token IDs if/when that stops being true.
4. **Social-only accounts can't use password-gated confirmation flows**
   (`enableMfa`/`disableMfa`/`changePassword`) since they have no password.
   Needs a "confirm via fresh Google ID token" path for those accounts.
5. **`clientIp()` reads `getRemoteAddr()` directly** — correct for a direct
   connection, wrong (returns the proxy's IP) once this sits behind a reverse
   proxy/load balancer. Needs `X-Forwarded-For` handling at that point.
6. **In-memory RSA keypair** (carried over from the original JWT work, not
   changed here) — still fine for one instance, still needs externalizing to
   PEM/env vars before running more than one instance.

None of the above are oversights; each is flagged at the point in the code
where it would need to change, with what the upgrade path is.
