# Engineering Log — Identity Auth Build Fix, Catalog/Studio Rebuild, and Real-DB Test Fixes

**Author:** Tanvir (via Claude Code)
**Date:** 2026-07-11
**Branch:** `develop`, all changes uncommitted at end of session
**Trigger:** Started as "there is breaking code in auth, run a build and check."
That turned up the same pre-existing broken-build state documented in
`doc/log/2026-07-07-feature-12-content-access-and-broken-build-triage.md`
(commit `aac7410`'s June 21 canonical-schema redesign left several
modules' services/DTOs pointing at deleted entities). Fixed identity first,
then — after asking "for Dev B, what is left to code?" — was told to build
out the rest: the whole of Dev B's remaining catalog/studio build-unblock,
normally out of scope for Dev A. Session ended with Docker Desktop becoming
available mid-session, which allowed a real `./gradlew test` run against
Postgres for the first time this session — and that run surfaced three more
previously-invisible bugs, including one **real production bug** (not just a
test artifact).

---

## Part 1 — Identity/auth build fix

### Root cause
Same class of bug as the `UserRole` problem from the 07-07 triage: the June 21
refactor ("derived, non-stored roles") deleted the `UserRole` enum but never
wrote replacement logic, leaving `AdminAuthorServiceImpl.ensureAdmin` and
`AuthorProfileServiceImpl.ensureAuthor` referencing a type that no longer
existed. Confirmed via `git log`/`git show` that `AuthorProfile.java` has been
byte-for-byte unchanged since `aac7410` — this was never fixed, not
regressed.

### Decisions asked and answered
1. **ADMIN-role derivation** — schema has zero admin concept (no column, no
   table). Chose: add `identity.users.is_admin` boolean (migration, at the
   time numbered `V3`, later renumbered — see Part 3). Rejected a config-based
   allowlist (not durable/queryable) and "stub it out" (the feature already
   has working controller/contract code, just needs a real check).
2. **AUTHOR-role derivation** — chose the already-implied answer from the
   07-07 log: "has an `AuthorProfile` row" (`authorProfileRepository.existsByUser_Id`),
   no enum needed at all.
3. **Payout DTO mismatch** — `AuthorProfileResponseDto`/`AuthorProfileUpdateRequestDto`
   called `getPayoutPhone()`/`setPayoutPhone()`, but the entity has
   `payoutMfsNumber` (String) + `payoutMfsProvider` (enum) instead of one phone
   field. Chose to expose both fields on the DTOs rather than collapsing to
   just the number, since the provider (bKash/Nagad/Rocket) is meaningful data
   the old single-field shape would silently drop.

### A second, previously-undiscovered instance of the same bug
While fixing the above, `AdminAuthorServiceImpl`/`AuthorProfileServiceImpl`
turned out to *also* call `authorProfile.isActive()`/`setActive()` on
`AuthorProfile` — a field that has never existed on that entity either, same
`aac7410` root cause, just missed in every earlier pass (including this
session's own first "identity compiles clean" claim, which was wrong — a
second `compileJava` after later catalog changes caught it). Fixed the same
way: added `identity.author_profiles.active` (boolean, default `true`).

### Files touched (Part 1)
- `identity/internal/entity/User.java` — `+admin` boolean field.
- `identity/internal/entity/AuthorProfile.java` — `+active` boolean field.
- `identity/internal/service/AdminAuthorServiceImpl.java` — `UserRole` check →
  `admin.isAdmin()`; payout getters fixed.
- `identity/internal/service/AuthorProfileServiceImpl.java` — `UserRole` check
  → `authorProfileRepository.existsByUser_Id(...)`; payout getters/setters
  fixed; unused `UserRepository` dependency removed.
- `identity/api/dto/response/AuthorProfileResponseDto.java`,
  `identity/api/dto/request/AuthorProfileUpdateRequestDto.java` — `payoutPhone`
  → `payoutMfsNumber` + `payoutMfsProvider`.
- Migrations: `identity/V10__add_user_is_admin.sql`,
  `identity/V14__add_author_profile_active.sql`.

---

## Part 2 — Catalog/studio rebuild (Dev B's remaining work)

### Scope decision
Asked directly: "for Dev B, what is left to code?" Answer inventoried four
independent gaps (author CRUD, tags, media, admin content-review), each with
its own open design question. Asked the user through each one rather than
guessing:

| Question | Answer chosen | Why |
|---|---|---|
| Where does author-facing CRUD (create/edit/publish/tags) live now? | **studio, not catalog** | `studio.writings` (via `Writing`) is the mutable draft row the June 21 redesign introduced; `catalog.published_writings` is an explicitly read-only, denormalized projection ("copied from studio.writings at publish time" per the migration's own comment). Catalog literally cannot correctly host draft CRUD without reaching into studio's `internal` package, which Spring Modulith is built to prevent. The `reading` module already set the precedent of depending on `studio::api-contract` for exactly this reason. |
| `published_writings.view_count` is missing vs. `like_count`/`comment_count` | **Add the column now** | Small, mechanical, matches an existing pattern on the same table. |
| Admin content-review (`UNDER_REVIEW`) — no such `WritingStatus` state exists | **Drop the feature** | Confirmed no review-queue concept anywhere in the canonical schema; the original doc marks it "optional" (17.2). Resurrecting it is a real design task, not today's job. |
| `PostMedia`'s dead post-FK — keep a link or drop it? | **Drop it, author-scoped log only** | Grepped the codebase: nothing has ever read the FK back (Feature 10.4 "link media to post" was never implemented). Keeping it would just be unused optionality. |

### Architecture built
- **`studio.AuthorWritingService`/`AuthorWritingServiceImpl`/`AuthorWritingController`**
  (new) — full author CRUD lifecycle ported from catalog's dead
  `AuthorPostServiceImpl` logic (slug generation, draft/publish state
  machine, FREE/LOCKED price validation) onto `Writing`/`WritingCategory`.
  One real schema gap found while porting: `studio.writings` never had
  `synopsis`/`cover_image_url` columns, but the projection needs both —
  added via migration (`V13`), not asked about since it's a straightforward
  completion, not a design fork.
  - `slug` is assigned only at **publish** time (per the schema's own
    comment on `studio.writings.slug`), not at draft creation — a detail
    almost missed by mechanically porting the old `Post` model's
    "slug-on-create" behavior.
  - Single-tenant MVP: `tenantId` hardcoded to `1L` (the seeded default
    tenant row), marked with a `ponytail:` comment naming the upgrade path
    (real tenant resolution) if multi-tenant ever ships.
- **Publish is event-driven.** `AuthorWritingServiceImpl.publish()`/
  `unpublish()` raise `WritingPublishedEvent`/`WritingUnpublishedEvent`
  (new, in studio's `api.event` package) via plain Spring
  `ApplicationEventPublisher` — **not** `@ApplicationModuleListener`;
  checked the actual resolved Gradle dependencies and confirmed
  `spring-modulith-events-api` is not on the classpath (`starter-core` alone
  doesn't pull it in), so this uses vanilla Spring
  `@TransactionalEventListener(phase = AFTER_COMMIT)` instead. Chosen over a
  direct synchronous call from studio into catalog because catalog's
  `package-info.java` already pre-declared `studio::api-event` as an allowed
  dependency (and nothing for the reverse direction) — strong evidence this
  was the intended seam, just never implemented.
  - Catalog's new `WritingPublicationEventListener`
    (`catalog/internal/listener/`) consumes both events, upserts/deletes
    `PublishedWriting`+`PublishedWritingTag` rows, and resolves the author's
    denormalized pen name via `identity::api-contract`
    (`AuthorProfileService.getPublicAuthorProfile`).
- **Tags = studio's `Category`/`WritingCategory`** (FK-based, already
  existed, unused), not a resurrected `Tag` entity (gone, no successor
  table, confirmed via grep of all migrations). Added a new
  `studio::api-contract` `CategoryService` (list all categories); catalog's
  `TagServiceImpl` now delegates to it instead of a dead `TagRepository`.
- **Media**: `PostMedia` → `WritingMedia` (renamed, stays in `catalog`
  module since `MediaUploadService`/`Controller` stay there), FK to a
  writing dropped entirely per the decision above — now just
  `authorId`/`storageKey`/`mimeType`/`fileSizeBytes`.
- **Catalog's read path rewritten**: `CatalogServiceImpl` now queries
  `PublishedWritingRepository` directly — full-text search via the
  existing generated `search_tsv` column (`plainto_tsquery`), tag filter via
  a join to `PublishedWritingTag`, and an atomic `view_count` increment
  (`@Modifying` JPQL) on detail fetch.
- **Deleted outright** (no successor needed, referenced entities gone since
  `aac7410`): `AuthorPostServiceImpl`/`Controller`/contract/its three DTOs,
  `AdminPostServiceImpl`/`Controller`/contract/its DTO, `PostRepository`,
  `TagRepository`, `PostTagRepository`.

### Module-boundary fixes required by `ModulithVerificationTests`
First verification run failed with two violations neither `compileJava` nor
manual review had caught (Modulith checks named-interface access, not just
Java visibility):
- `catalog`'s new event listener uses `identity`'s
  `PublicAuthorProfileResponseDto` — needed `identity::api-response-dto`
  added to catalog's `allowedDependencies`, alongside the existing
  `identity::api-contract`.
- `catalog`'s `TagServiceImpl` uses studio's `CategoryResponseDto` — needed
  `studio::api-response-dto` added alongside `studio::api-contract`.

Both added; `ModulithVerificationTests` passes clean.

---

## Part 3 — Flyway version collision (found and fixed in passing)

While numbering new migrations, discovered `develop` already had a
pre-existing, **unfixed** collision: three files all claimed version `V2`
(`catalog/V2__seed_tags.sql`, `identity/V2__auth_enterprise_hardening.sql`,
`tenant/V2__init_tenant.sql`) — `spring.flyway.locations` combines every
module's migration folder into one global version namespace, so this would
have rejected outright on a fresh `flyway migrate`.

A fix for exactly this already existed, unmerged, on
`origin/fix/a-flyway-and-auth-hardening` (commit `184ef8f`,
"fix(db): dedupe Flyway V2 migrations") — deletes the orphaned
`catalog/V2__seed_tags.sql` (it inserted into a `catalog.tags` table that no
longer exists) and renames `identity/V2__auth_enterprise_hardening.sql` to
`V9`. Diffed that commit against current `develop` to confirm it touches
*only* those two migration files (the branch also has other, unrelated
auth-security work not pulled in here) and replicated just that isolated
rename/delete directly on `develop`, rather than merging the whole branch.

Final migration sequence: `V0`–`V9` (renumbered), `V10` (`is_admin`), `V11`
(`view_count`), `V12` (`writing_media`), `V13` (`writings` synopsis/cover),
`V14` (`author_profiles.active`) — all confirmed unique.

---

## Part 4 — Real-DB verification (Docker became available mid-session)

Once Docker Desktop was available, ran the full test suite for real —
`ModulithVerificationTests` had already been checked structurally (no DB
needed), but nothing had exercised `@SpringBootTest` against actual Postgres
yet this session.

**Environment note:** `docker compose up` failed — port `5432` was already
bound. Investigation found a **native (non-Docker) PostgreSQL 16** already
running on the host with a matching `mvp` database and matching credentials
(`postgres`/`psql` from `.env`), already carrying a `V0`–`V8` Flyway history
from some earlier point. Used that directly rather than fight the running
system service; only the compose-managed Redis container was actually
needed/started.

First full run: 19 tests, only 5 passing (everything except the two
DB-integration test classes). Fixing what turned up, in order:

1. **`ModulithVerificationTests`** — already covered in Part 2.
2. **`AuthServiceIntegrationTests`** (10 of 11 failing) —
   class-level `@Transactional` (auto-rollback) is fundamentally
   incompatible with `SecurityAuditServiceImpl.record()`'s
   `@Transactional(propagation = REQUIRES_NEW)`: the audit write suspends
   the outer transaction and starts a genuinely separate one, which under
   Postgres `READ COMMITTED` can never see a row the outer (about-to-roll-back)
   transaction hasn't actually committed yet — hence `user_id` foreign-key
   violations on every audit-log insert. Fixed by removing the class-level
   `@Transactional` and replacing it with explicit cleanup: track created
   user IDs, delete them (audit logs → sessions → user row, respecting FK
   order) in `@AfterEach`.
   - **Non-obvious sub-bug**: a first attempt annotated `tearDown()` itself
     with `@Transactional` — this silently does nothing, because Spring's
     `TransactionalTestExecutionListener` only wraps `@Test`-annotated
     methods, not arbitrary `@AfterEach`/`@BeforeEach` lifecycle callbacks.
     Fixed by using a manual `TransactionTemplate` instead, which doesn't
     depend on that listener at all.
3. **Real production bug found via the same investigation, not just a test
   issue**: `AuthServiceImpl.loginWithGoogle()` creates a brand-new `User`
   row and then calls `securityAuditService.record(...)` (`REQUIRES_NEW`) —
   **in the same top-level transaction**. Exactly the same conflict as #2,
   except this isn't test-rollback semantics; it's the method's *own*
   transaction still being open (uncommitted) when `REQUIRES_NEW` suspends
   it. **Any genuinely first-time Google sign-in would fail this way in
   production.** Every other login path in this class was safe by
   coincidence — they only ever operate on a user that was created and
   committed in a wholly separate, already-finished prior transaction.
   Fixed by wrapping just the find-or-create-and-save block in
   `loginWithGoogle` in its own `TransactionTemplate` with
   `PROPAGATION_REQUIRES_NEW`, so the new user row is durably committed
   before the audit write runs.
4. **`IdentityServiceIntegrationTests`** — hardcoded OTP `"123456"`, a
   leftover from when `IdentityServiceImpl.verifyOtp` was an unconditional
   stub (see `doc/log/2026-07-01-...`, §1: "OTP verification was entirely
   fake"). `OtpServiceImpl` has generated real random 6-digit codes for over
   a week; this test could never have passed against the real
   implementation. Fixed by moving the test from package
   `com.prishtha.mvp.identity` into
   `com.prishtha.mvp.identity.internal.service` (to reach the
   package-private `OtpDeliveryService`, mirroring the pattern
   `AuthServiceIntegrationTests` already used) and extracting a shared
   `CapturingOtpDeliveryTestSupport` (`@TestConfiguration` + a capturing
   `OtpDeliveryService`) so both integration test classes reuse one
   mechanism instead of duplicating it.

Final run: **19/19 tests pass**, `compileJava`/`compileTestJava` clean,
`ModulithVerificationTests` clean, Flyway migrated `V9`–`V14` cleanly onto
the pre-existing `V0`–`V8` history with no manual intervention.

---

## Known limitations / deliberate shortcuts (ponytail-marked in code)

1. **Single-tenant `tenantId` hardcoded to `1L`** in
   `AuthorWritingServiceImpl` — matches the seeded default tenant row;
   needs real tenant resolution (JWT claim/`TenantService`) if multi-tenant
   ever ships.
2. **`spring-modulith-events-api` is not a dependency** — the
   publish/unpublish event flow uses plain Spring
   `@TransactionalEventListener`, not Modulith's own event
   infrastructure/persistence. Fine at current scale (synchronous,
   same-thread, no event-loss risk since nothing is `@Async`); revisit if
   the project later adds the Modulith events starter for other reasons.
3. **`AuthorPostServiceImpl`'s reading-module-facing behaviors that had no
   1:1 analog** (view-count tracking previously lived nowhere on the old
   `Post` model in a way `reading` depended on) were not re-examined beyond
   what `CatalogServiceImpl` itself needed — no cross-module regression
   expected, but not explicitly re-verified against `reading`'s Feature 12
   code path in this session.

None of the above are oversights; each is a scoped, explicit decision with
its own upgrade path.

---

## Full list of files touched

**New:**
- `catalog/internal/entity/WritingMedia.java`,
  `catalog/internal/repository/WritingMediaRepository.java`,
  `catalog/internal/listener/WritingPublicationEventListener.java`
- `studio/api/contract/AuthorWritingService.java`,
  `studio/api/contract/CategoryService.java`
- `studio/api/controller/AuthorWritingController.java`
- `studio/api/dto/request/AuthorWritingUpsertRequestDto.java`,
  `studio/api/dto/request/AssignWritingCategoriesRequestDto.java`
- `studio/api/dto/response/AuthorWritingResponseDto.java`,
  `studio/api/dto/response/CategoryResponseDto.java`
- `studio/api/event/WritingPublishedEvent.java`,
  `studio/api/event/WritingUnpublishedEvent.java`
- `studio/internal/service/AuthorWritingServiceImpl.java`,
  `studio/internal/service/CategoryServiceImpl.java`
- `studio/internal/util/constant/StudioRouteConstant.java`
- `test/.../identity/internal/service/CapturingOtpDeliveryTestSupport.java`
- Migrations: `identity/V10__add_user_is_admin.sql`,
  `catalog/V11__add_view_count.sql`, `catalog/V12__add_writing_media.sql`,
  `studio/V13__add_writing_synopsis_and_cover.sql`,
  `identity/V14__add_author_profile_active.sql`

**Deleted:**
- `catalog/api/contract/{AdminPostService,AuthorPostService}.java`
- `catalog/api/controller/{AdminPostController,AuthorPostController}.java`
- `catalog/api/dto/request/{AdminPostReviewRequestDto,AssignPostTagsRequestDto,AuthorPostUpsertRequestDto}.java`
- `catalog/api/dto/response/AuthorPostResponseDto.java`
- `catalog/internal/repository/{PostRepository,TagRepository,PostTagRepository,PostMediaRepository}.java`
- `catalog/internal/service/{AdminPostServiceImpl,AuthorPostServiceImpl}.java`
- `db/migration/catalog/V2__seed_tags.sql` (orphaned, dead)

**Renamed:**
- `db/migration/identity/V2__auth_enterprise_hardening.sql` →
  `V9__auth_enterprise_hardening.sql`
- `test/.../identity/IdentityServiceIntegrationTests.java` →
  `test/.../identity/internal/service/IdentityServiceIntegrationTests.java`

**Modified:**
- `identity/internal/entity/{User,AuthorProfile}.java`
- `identity/internal/service/{AdminAuthorServiceImpl,AuthorProfileServiceImpl,AuthServiceImpl}.java`
- `identity/internal/repository/{SecurityAuditLogRepository,UserSessionRepository}.java`
  — added `deleteByUser_Id`
- `identity/api/dto/response/AuthorProfileResponseDto.java`,
  `identity/api/dto/request/AuthorProfileUpdateRequestDto.java`
- `catalog/api/dto/response/{PostDetailResponseDto,PostSummaryResponseDto,TagResponseDto}.java`
- `catalog/internal/entity/PublishedWriting.java` — `+viewCount`
- `catalog/internal/repository/{PublishedWritingRepository,PublishedWritingTagRepository}.java`
- `catalog/internal/service/{CatalogServiceImpl,MediaUploadServiceImpl,TagServiceImpl}.java`
- `catalog/package-info.java` — `+studio::api-contract`,
  `+studio::api-response-dto`, `+identity::api-response-dto`
- `studio/internal/entity/Writing.java` — `+synopsis`, `+coverImageUrl`
- `studio/internal/repository/{CategoryRepository,WritingCategoryRepository,WritingRepository}.java`
- `studio/package-info.java` — `+identity::api-contract`
- `test/.../identity/internal/service/AuthServiceIntegrationTests.java`

**Not touched by me, changed by a concurrent session:** `compose.yaml`
(another session's develop/main-sync work, per its own log the same day).

---

## Verification performed

- `./gradlew compileJava` — clean (was 68 errors at session start, all in
  identity + catalog).
- `./gradlew compileTestJava` — clean.
- `./gradlew test` (`ModulithVerificationTests` structural, no DB needed) —
  clean, after adding the two missing `allowedDependencies` entries.
- Once Docker Desktop became available: full `./gradlew test` against the
  host's native Postgres 16 + a Docker Redis container — **19/19 passing**
  after the three real-DB-only bugs above were fixed. Flyway migrated
  `V9`–`V14` cleanly onto the pre-existing `V0`–`V8` history.
- Confirmed no leftover test data: `AuthServiceIntegrationTests`' new
  `tearDown()` correctly removes every user/session/audit row it creates,
  verified via direct `psql` query after a full suite run.
