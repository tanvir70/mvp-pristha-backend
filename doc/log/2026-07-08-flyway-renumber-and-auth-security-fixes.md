# Engineering Log — Flyway Renumber + Auth Security Fixes

**Date:** 2026-07-08
**Trigger:** Full Dev A code review found (a) three migrations claiming V2 so
Flyway can't migrate a fresh DB, and (b) four security gaps in shipped auth /
content-access code. User directed: catalog + `UserRole` compile fixes go to
Dev B; this session fixes Flyway and the security gaps.

## Flyway

- `identity/V2__auth_enterprise_hardening.sql` → **V9** (V0–V9 now unique:
  shared 0, identity 1+9, tenant 2, studio 3, catalog 4, social 5, reading 6,
  billing 7, analytics 8).
- Deleted `catalog/V2__seed_tags.sql` — inserted into `catalog.tags`, a table
  that no longer exists. Tag seeding is Dev B's Feature 9 to rebuild against
  `published_writing_tags`.
- **Verified:** applied all 10 files in version order via psql to a scratch DB
  (`mvp_migration_check`) — zero errors, all 8 module schemas created.
  (App still can't boot — pre-existing compile errors, Dev B's item.)
- If a local DB was migrated back when identity's hardening file was still V2,
  drop/recreate it (or `flyway repair`) — the history row won't match.

## CI

- Added `.github/workflows/build.yml`: `compileJava compileTestJava` on PRs
  and pushes to main/develop. Deliberately compile-only; it stays **red until
  Dev B fixes catalog/UserRole**, which is the point — no more merging on top
  of broken builds. Upgrade to `./gradlew test` + service containers after.

## Security fixes

1. **Paywall bypass** — `ContentAccessController` no longer takes `readerId`
   as a query param; derives it from `@AuthenticationPrincipal Jwt`
   (null → guest → preview). API change: `?readerId=` is gone; send a Bearer
   token instead.
2. **Google account takeover** — `GoogleTokenVerifierServiceImpl` rejects
   tokens whose `email_verified` != true (login links accounts by email).
3. **Suspended-user refresh** — `AuthServiceImpl.refreshToken` now requires
   `UserStatus.ACTIVE`; suspended users can no longer mint access tokens for
   the rest of their 30-day refresh window.
4. **Signup/password validation (SRS ID-FR-01/08)** — new
   `AuthValidationConstant` (BD phone + password-complexity regexes);
   `UserSignUpRequestDto` gets phone `@Pattern`, password complexity,
   `confirmPassword` + `@AssertTrue` match check; same complexity on
   `ResetPasswordRequestDto.newPassword` and
   `ChangePasswordRequestDto.newPassword`; `@Valid` added to
   `IdentityController.signUp`. **API change:** signup body now requires
   `confirmPassword`.

## Verification

- `./gradlew compileJava`: still exactly the 68 pre-existing errors, all in
  catalog + the two known `UserRole` files — zero errors in any touched file.
- Regexes sanity-checked against valid/invalid phones and passwords (script
  run, all assertions pass).
- `./gradlew test` still blocked by the pre-existing compile errors.

## Left open (unchanged from the review)

- ADMIN role scheme + `AuthorProfileServiceImpl`/`AdminAuthorServiceImpl`
  (Dev B, with the catalog rebuild).
- Features 4, 13, 14, 15, 16; OTP resend (ID-FR-02b); `grantUnlock`/`hasAccess`
  on the reading contract (READ-FR-02) before Feature 13.
- Refresh check-then-delete race; LOGIN_SUCCESS-before-MFA audit semantics.
