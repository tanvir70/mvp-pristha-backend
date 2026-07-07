# Action Plan — JWT Login / Logout (`identity` module)

**Scope:** SRS `ID-FR-03` (Login), `ID-FR-04` (Token Refresh), `ID-FR-05` (Logout), `NFR-04` (auth abuse resistance). Pure `identity` module work — independent of the currently-broken `catalog`/`AuthorRequest` code, which is being handled separately.

---

## 1. Token design

| Token | Type | TTL | Storage | Revocable? |
|---|---|---|---|---|
| Access | JWT, RS256 | 15 min | none (self-verifying) | No — expires naturally |
| Refresh | Opaque UUID | 30 days | Redis `refresh:{userId}:{tokenId}` | Yes — delete the key |

**Why split like this:** a JWT is only worth using if it's stateless (no DB/Redis hit per request, virtual-thread-friendly), but stateless tokens can't be revoked without a blacklist — which the SRS explicitly rejects ("No access-token blacklist in MVP... revoking the refresh token stops renewal"). So only the refresh token needs to be revocable, and an opaque server-side token revokes itself for free by being deleted. JWT for the thing that must self-verify, opaque token for the thing that must be killable.

**Why RS256, not HS256:** `coding_guideline.md`'s premise is "split into microservices later." RS256 (asymmetric) lets any future service verify tokens with just the public key, never holding the signing secret. Costs nothing extra now, avoids a re-key exercise later. SRS already specifies RS256.

---

## 2. New dependency: `spring-boot-starter-oauth2-resource-server`

Add to `build.gradle`. This is Spring Security's own JWT-bearer module — `http.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))` wires the `BearerTokenAuthenticationFilter`, signature verification, expiry checks, and claim→authority mapping automatically. The same dependency brings `NimbusJwtEncoder` for *issuing* tokens at login.

**Why this over hand-rolling a filter or pulling in `jjwt`:** signature verification and expiry checking are easy to get subtly wrong (timing attacks, clock skew, algorithm confusion). Spring Security already does this correctly and is already half-installed (`spring-boot-starter-security` is in the classpath). Reaching for the native Spring Security JWT module beats writing or importing a parallel implementation.

---

## 3. New dependency: `spring-boot-starter-data-redis`

Needed for: refresh-token store (with rotation) + login lockout counters.

**Why now:** `coding_guideline.md` §L already anticipated this — "adopt when the need arrives... define a `CacheService` contract... put the actual `StringRedisTemplate` calls behind a single `RedisService` implementation." This is that arrival. Follow that rule exactly: a `CacheService` interface + one `RedisCacheServiceImpl`, nothing else touches `StringRedisTemplate` directly.

**Why Redis and not a `refresh_tokens` table:** Redis TTL expires rows for free. A DB table needs a cleanup job for expired rows and gives nothing a JOIN ever needs. No new Flyway migration for this feature.

---

## 4. RSA keypair

Generate a 2048-bit `KeyPair` once at startup in a `@Configuration` bean, held in memory; expose `JwtEncoder`/`JwtDecoder` beans built from it.

```java
// ponytail: in-memory keypair — fine for single-instance MVP (Render, one dyno).
// Upgrade when: scaling to >1 instance (tokens issued by one instance won't
// verify on another) or a restart needs to preserve outstanding tokens —
// then externalize via PEM files / env vars instead of generating fresh.
```

---

## 5. Files to add

```
identity/api/contract/AuthService.java               — login(), refreshToken(), logout()
identity/api/dto/request/LoginRequestDto.java         — phone, password
identity/api/dto/request/RefreshTokenRequestDto.java  — refreshToken
identity/api/dto/request/LogoutRequestDto.java        — refreshToken
identity/api/dto/response/AuthTokenResponseDto.java   — accessToken, refreshToken, expiresIn, tokenType, userId, fullName, roles, authorProfileId
identity/api/controller/AuthController.java            — POST /api/v1/auth/login, /refresh, /logout
identity/internal/service/AuthServiceImpl.java         — package-private impl
identity/internal/service/CacheService.java            — interface: store/get/delete refresh token, incr+expire lockout counter
identity/internal/service/RedisCacheServiceImpl.java   — only class touching StringRedisTemplate
identity/internal/config/JwtConfig.java                — RSA keypair, JwtEncoder/JwtDecoder beans
identity/internal/util/constant/IdentityRouteConstant.java — AUTH_BASE_PATH, LOGIN, REFRESH, LOGOUT
shared/exception/AuthenticationFailedException.java    — new, maps to 401
```

**Why a separate `AuthService` instead of folding into `IdentityService`:** the module already splits concerns one-contract-per-concern (`AuthorProfileService` vs `IdentityService`). Signup/OTP and login/refresh/logout are different lifecycles; keeping them apart matches the existing pattern and keeps each interface small.

**Why no `UserDetailsService`/`AuthenticationManager`:** that machinery exists for Spring Security's session/form-login flow. For a stateless API that does one BCrypt comparison and hands back a JWT, calling `passwordEncoder.matches(raw, user.getPasswordHash())` directly in `AuthServiceImpl` is less code and exactly what the comparison needs — standing up the full `AuthenticationManager` chain just to throw it away after one check would be the over-engineered version.

**Why one new exception (`AuthenticationFailedException` → 401)** instead of reusing `BusinessRuleViolationException` (400): bad credentials/locked account/expired refresh token are textbook 401, and `GlobalExceptionHandler` already maps exception type → status (§J of the coding guideline) — this is one more line in that same handler, not a new pattern.

---

## 6. Files to change

**`SecurityConfig.java`**
- Add `.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))`
- Add `.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))`
- Explicit `.httpBasic(AbstractHttpConfigurer::disable)` — right now nothing actually enforces auth (no provider configured), so every "protected" endpoint is silently open; this is the change that makes `anyRequest().authenticated()` real for the first time.
- A `JwtAuthenticationConverter` bean mapping the JWT's `roles` claim into `GrantedAuthority`s (derived `READER`/`AUTHOR`, computed once at login — never re-derived per-request).

**`IdentityService` contract** — add `isUserActive(Long)`, `isAuthor(Long)`, `getUserBasicInfo(Long)` per the SRS `identity::api-contract` (§3.A). Not used by login/logout itself, but this is the natural place to land them since they're trivial repository lookups other modules will eventually call.

---

## 7. Login-failure handling (`NFR-04`)

- `login_attempts:{phone}` — Redis `INCR` + `EXPIRE`; on the 5th failure, set `login_lock:{phone}` with a 15-minute TTL.
- Same generic "invalid credentials" message and exception (`AuthenticationFailedException`) whether the phone doesn't exist or the password is wrong — distinguishing the two would let an attacker enumerate registered phone numbers.

---

## 8. Explicitly out of scope

- Wiring the OTP flow to real Redis (currently mocked with hardcoded `123456` — a pre-existing shortcut, not part of this feature).
- Migrating the existing `requesterUserId`/`authorProfileId` query params (`AuthorProfileController`, `AuthorPostController`, etc.) to `@AuthenticationPrincipal` — natural next step once this lands, but touches files outside `identity` and is its own PR.
- Catalog module fixes (broken `Post`/`Tag`/`PostTag` references) — separate, as agreed.

---

## 9. Self-check

One `AuthServiceIntegrationTests` (mirrors the existing `IdentityServiceIntegrationTests` pattern):
- Login success issues a verifiable JWT + refresh token.
- Wrong password fails with 401.
- 5 consecutive failures locks the account for 15 minutes.
- Refresh rotates (the old refresh token no longer works after one use).
- Logout invalidates the refresh token.
