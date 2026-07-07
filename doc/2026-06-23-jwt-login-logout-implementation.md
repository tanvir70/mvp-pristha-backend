# JWT Login / Logout — Implementation Record (`identity` module)

**Author:** Tanvir
**Date:** 2026-06-23

Implements SRS `ID-FR-03` (Login), `ID-FR-04` (Token Refresh), `ID-FR-05` (Logout),
`NFR-04` (auth abuse resistance). Companion to `jwt-login-logout-action-plan.md` —
that file is the plan, this is what was actually built and why each choice was made.

---

## 1. What was built

### Token scheme
| Token | Format | TTL | Storage | Revocable |
|---|---|---|---|---|
| Access | JWT, RS256 | 15 min | none (self-verifying) | No — expires naturally |
| Refresh | `{userId}:{UUID}` opaque | 30 days | Redis `refresh:{userId}:{tokenId}` | Yes — delete the key |

### Endpoints (`AuthController`, `POST` only)
- `POST /api/v1/auth/login` — phone + password → `AuthTokenResponseDto`
- `POST /api/v1/auth/refresh` — refresh token → new access + new refresh (rotation)
- `POST /api/v1/auth/logout` — refresh token → `204 No Content`

### Files added
```
identity/api/contract/AuthService.java                      login/refresh/logout contract
identity/api/controller/AuthController.java                 the three POST endpoints
identity/api/dto/request/LoginRequestDto.java              phone, password (@NotBlank)
identity/api/dto/request/RefreshTokenRequestDto.java       refreshToken (@NotBlank)
identity/api/dto/request/LogoutRequestDto.java             refreshToken (@NotBlank)
identity/api/dto/response/AuthTokenResponseDto.java        tokens + basic profile + roles
identity/internal/service/AuthServiceImpl.java             package-private impl
identity/internal/service/CacheService.java                cache contract (refresh + lockout)
identity/internal/service/RedisCacheServiceImpl.java       only class touching StringRedisTemplate
identity/internal/config/JwtConfig.java                    RSA keypair, JwtEncoder/JwtDecoder beans
identity/internal/config/AuthProperties.java               typed binding for identity.auth.*
identity/internal/util/constant/IdentityRouteConstant.java AUTH_BASE_PATH, LOGIN, REFRESH, LOGOUT
shared/exception/AuthenticationFailedException.java         maps to 401
src/test/.../identity/AuthServiceIntegrationTests.java     5-case self-check
```

### Files changed
```
build.gradle                  + oauth2-resource-server, data-redis, validation, springdoc(swagger)
compose.yaml                  + redis service
application.properties        + redis host/port, identity.auth.* token/lockout settings
SecurityConfig.java           stateless, oauth2ResourceServer JWT, role converter, swagger paths
IdentityService(+Impl).java   + isUserActive, isAuthor, getUserBasicInfo (SRS contract methods)
GlobalExceptionHandler.java   + AuthenticationFailedException -> 401
```

---

## 2. Decision record

### Why JWT for access, opaque token for refresh
Two tokens because the two have **opposite requirements**. The access token is checked
on every request and the SRS explicitly waives instant revocation — so a self-verifying
JWT (no per-request Redis hit) is the win, with the 15-min TTL as the accepted exposure
window. The refresh token *must* be instantly revocable (logout), so it always hits Redis
anyway; a JWT there would add signature/parsing cost and leak its claims (JWTs are signed,
not encrypted) for zero benefit. So: JWT where statelessness matters, opaque key where
revocability matters.

### Why RS256, not HS256
Asymmetric. Only the identity service holds the private (signing) key; any future
microservice verifies with the public key alone and can never forge tokens. HS256 would
force sharing the signing secret with every verifier. The SRS specifies RS256; starting
with it now avoids a hard re-key cutover later. ES256/EdDSA are technically leaner but the
SRS fixed RS256 and it has the widest tooling compatibility; signing only happens at
login/refresh (not per request) so RSA's slower signing is irrelevant.

### Why the refresh token is `{userId}:{tokenId}`
`userId` is a public selector for the Redis key; `tokenId` (a `UUID`, 122 bits of
`SecureRandom`) is the unguessable secret. This lets validation be a single direct
`GET refresh:{userId}:{tokenId}` instead of a scan-by-value. Rotation deletes the old key
and writes a new one on each refresh, so a captured-but-already-rotated token fails replay.

### Why no `UserDetailsService` / `AuthenticationManager`
That machinery exists for Spring's session/form-login chain. For a stateless API doing one
`passwordEncoder.matches(...)` and issuing a JWT, calling the encoder directly in the
service is less code and exactly the check needed. Standing up the full manager chain just
to discard it after one comparison would be the over-engineered path.

### Why a separate `AuthService` (not folded into `IdentityService`)
The module already splits one-contract-per-concern (`AuthorProfileService` vs
`IdentityService`). Signup/OTP and login/refresh/logout are different lifecycles; keeping
them apart matches the existing pattern and keeps each interface small.

### Why one new exception `AuthenticationFailedException` -> 401
Bad credentials / locked account / expired refresh token are textbook 401, not the 400 of
`BusinessRuleViolationException`. `GlobalExceptionHandler` already maps exception type ->
status, so this is one more handler method, not a new pattern.

### Redis via `CacheService` + single `RedisCacheServiceImpl`
Per coding-guideline §L: no service touches `StringRedisTemplate` directly. One interface,
one impl, Redis stays swappable/testable. Redis is used for exactly two unrelated things —
the refresh-token store (rotation + revocation) and login-lockout counters
(`login_attempts:{phone}` INCR+EXPIRE, `login_lock:{phone}` on the 5th failure). It never
touches the access JWT — that is the whole point of the JWT being self-verifying.

### Login-failure handling (NFR-04)
Same generic "Invalid phone number or password" whether the phone is unknown or the
password is wrong — distinguishing them would let an attacker enumerate registered phones.
5 failures within a 15-min window locks login for 15 min.

### In-memory RSA keypair (deliberate shortcut)
Generated once at startup, held in memory. Marked with a `ponytail:` comment in
`JwtConfig`. Fine for a single-instance MVP. Upgrade trigger: scaling past one instance
(tokens issued by one won't verify on another) or needing tokens to survive a restart —
then externalize the keypair via PEM/env.

### `AuthProperties` as a `@ConfigurationProperties` record
Token TTLs, lockout window/count live in `application.properties` under `identity.auth.*`
and bind into an immutable record (`AuthProperties`). This is Spring's documented
type-safe-config convention over `@Value`: relaxed binding, env-var override for
containers, fail-fast typing, one bean instead of scattered string lookups. Record +
constructor binding is the current idiom (Boot 2.2+), not the older mutable getter/setter
class.

### Exception message strings kept as in-class `static final`
`INVALID_CREDENTIALS_MESSAGE` / `INVALID_REFRESH_TOKEN_MESSAGE` stay inside
`AuthServiceImpl` — each is used only there. Extracting single-use strings into a shared
constants class reduces locality for no reuse (the "constants class" antipattern). If the
same message is ever thrown from a second service, promote it then. True multi-language
needs would call for `MessageSource`/resource bundles, which is out of scope for a
single-language MVP.

### Swagger / OpenAPI
Added `springdoc-openapi-starter-webmvc-ui:3.0.3` (the v3 line targeting Spring Boot 4).
`SecurityConfig` permits `/swagger-ui/**` and `/v3/api-docs/**`. UI at
`/swagger-ui/index.html`.

### `SecurityConfig` is now real
Before this change nothing enforced auth (no provider configured) — every "protected"
endpoint was silently open. Adding `oauth2ResourceServer().jwt(...)` +
`SessionCreationPolicy.STATELESS` + explicit `httpBasic disable` makes
`anyRequest().authenticated()` actually enforced for the first time. A
`JwtAuthenticationConverter` maps the JWT `roles` claim (computed once at login, never
re-derived per request) to `ROLE_`-prefixed authorities.

---

## 3. Self-check (`AuthServiceIntegrationTests`)
- Login success issues a verifiable 3-part JWT + a refresh token.
- Wrong password fails (`AuthenticationFailedException`).
- 5 consecutive failures lock the account (correct password then also rejected).
- Refresh rotates: the old refresh token no longer works after one use.
- Logout invalidates the refresh token.

---

## 4. Out of scope (unchanged from the plan)
- Wiring OTP to real Redis (still hardcoded `123456`).
- Migrating `requesterUserId`/`authorProfileId` query params to `@AuthenticationPrincipal`.
- Catalog module fixes.
