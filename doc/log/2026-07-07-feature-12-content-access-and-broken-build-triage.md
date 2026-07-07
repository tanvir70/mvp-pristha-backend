# Engineering Log — Feature 12 (Content Access) + Broken-Build Triage

**Date:** 2026-07-07
**Trigger:** Asked to start Feature 12 (Content Reading & Access Control, per
`doc/mvp-feature-breakdown.md`). Before writing any code, ran `./gradlew
compileJava` to get a baseline — it failed on `develop`, on a clean working
tree, with no changes of mine in play yet. That discovery reshaped the whole
session: what was meant to be "build one feature" became "figure out how
broken the foundation is, get user decisions on what to fix vs. leave, then
build the feature on the part that's sound."

Scope actually touched: `reading` module (new), a small additive contract in
`studio`, and deletion of dead code in `identity`. `catalog`'s broken files
and `identity`'s role/deactivation code were diagnosed but deliberately left
untouched — see Part 2.

---

## Part 0 — Permission-prompt reduction (unrelated, done first)

Before the build investigation, added `.claude/settings.json` (project-level,
committed) allowing `Read`/`Edit`/`Write` anywhere under the repo path and
read-only search commands (`grep`, `rg`, `find`, `ls`, `cat`, `head`, `tail`,
`wc`, `pwd`) without a permission prompt each time. Destructive commands
(`rm`, force-push, etc.) were deliberately left gated. Chose the committed
project file over a personal `settings.local.json` so Dev B benefits too,
matching the update-config skill's own guidance table (project settings →
team-wide permissions, committed).

---

## Part 1 — Discovery: `develop` does not compile

### Root cause
Commit `aac7410` ("refactor: migrate entities, repositories, and migrations
to canonical DB design", merged via PR #11, 2026-06-21) replaced the old
flat-post / single-balance-wallet / admin-gated-author schema with the
canonical design in `doc/pristha_database_design_mvp.md`
(`studio.writings` → `catalog.published_writings` projection, double-entry
billing ledger, derived non-stored roles, open author onboarding). It
rewrote entities, repositories, and Flyway migrations — but did **not**
update the services/controllers/DTOs that were built against the old
entities. Everything merged afterward (media upload, admin tools, exception
handling — PRs #9, #10, plus two direct commits) landed on top of this
broken base without anyone running a full `compileJava`.

### Full inventory of what was broken (as found, before any fix)

**Catalog module** — every file still imports the deleted `Post`,
`PostStatus`, `PricingType`, `PostMedia`, `PostTag`, `Tag`:
- `CatalogServiceImpl`, `CatalogController`'s two response DTOs
  (`PostDetailResponseDto`, `PostSummaryResponseDto`)
- `AuthorPostServiceImpl` + its request/response DTOs
  (`AuthorPostUpsertRequestDto`, `AuthorPostResponseDto`,
  `AssignPostTagsRequestDto`)
- `AdminPostServiceImpl` + `AdminPostReviewRequestDto`
- `MediaUploadServiceImpl` (uses `PostMedia`, an entity with no replacement
  anywhere in the new migrations)
- `TagServiceImpl`, `TagRepository`, `PostTagRepository`, `PostRepository`,
  `PostMediaRepository`

This isn't a rename-only fix: the old code is a single mutable `Post` row
that flips between `DRAFT`/`PUBLISHED`/`UNDER_REVIEW`. The new schema splits
that into a mutable `studio.writings` row (draft/edit, `body_json`) and a
denormalized read-only `catalog.published_writings` projection created at
publish time. `WritingStatus` (new) also has no `UNDER_REVIEW` state, so
`AdminPostServiceImpl`'s content-moderation feature (doc Feature 17.2) may
have been designed away entirely, same as the author-approval flow below —
unconfirmed, a real design question for whoever rebuilds this.

**Identity module** — two independent problems, not one:
1. `AuthorRequestController`, `AdminAuthorRequestController`,
   `AuthorRequestService`/`Impl`, `AuthorRequestRepository`, and three DTOs
   (`AuthorRequestSubmitRequestDto`, `AuthorRequestRejectRequestDto`,
   `AuthorRequestResponseDto`) all reference the deleted `AuthorRequest`
   entity. Checked `V1__init_identity.sql`: the new schema has **no
   `author_requests` table**, confirming this is dead code for a removed
   feature (doc Feature 5, "author request & admin approval"), not a rename
   target — the June 21 commit message explicitly says "open author
   onboarding."
2. `AdminAuthorServiceImpl` (deactivate/activate author, doc Feature 17.3)
   and — more importantly — `AuthorProfileServiceImpl` (Feature 6, marked
   ✅ done in the feature-breakdown doc) both reference a `UserRole` enum
   that **doesn't exist anywhere in the codebase**. The refactor commit
   message calls roles "derived (non-stored)," but that derivation logic was
   never written. `AuthorProfileServiceImpl` additionally calls
   `getPayoutPhone()`/`setPayoutPhone()` on `AuthorProfile`, which has
   `payoutMfsNumber`/`payoutMfsProvider` instead — a second, unrelated break
   in the same file.
3. `SecurityConfig` is a stub: no JWT filter, no `@CurrentUser`, no
   role-based route protection. Feature 3.6–3.8 (JWT filter, current-user
   helper, role checks) are checked ✅ in the doc but were never built.

**Flyway (noted, not fixed — out of scope for this session):**
- `V2__seed_tags.sql` inserts into `catalog.tags`, a table that doesn't exist
  in the current `V4__init_catalog.sql` (tags are now
  `catalog.published_writing_tags`, a denormalized string-tag table, not an
  entity-backed `tags` table).
- Two files both claim version `V2` (`V2__seed_tags.sql` and
  `V2__init_tenant.sql`) — Flyway will reject this outright on a fresh
  migrate. Never actually hit because the app doesn't compile yet, so nobody
  has run it against a real DB recently.

### Why this matters beyond Feature 12
The ✅ marks in `doc/mvp-feature-breakdown.md` for Features 2–4 and 6 are
stale — that code hasn't compiled since 2026-06-21, three merged PRs ago.
Nobody caught it because nothing ran a full `./gradlew compileJava` after
that refactor landed.

---

## Part 2 — Decisions made (asked the user directly, in order)

#### Decision 1 — how much of this to fix before Feature 12
Options offered: fix catalog + identity fully / fix only what blocks Feature
12 / go sync with Dev B first.
**Chosen: fix only what blocks Feature 12.**
Reasoning given: catalog is Dev B's owned feature area and a full rewrite
there is a large, judgment-heavy design task, not something to do solo
mid-session.

#### Decision 2 — identity's dead `AuthorRequest` code
Options offered: delete it (matches open-onboarding schema) / add back an
`author_requests` table to keep the old admin-approval feature / leave it
broken and decide later.
**Chosen: delete the dead code.**
Reasoning given: the canonical schema has no `author_requests` table at all;
keeping the code alive would mean reintroducing a table the June 21 redesign
deliberately removed, contradicting the current design intent.

#### Decision 3 — catalog's Post/Tag/Media/Admin-review re-architecture
Once it became clear this wasn't a mechanical rename (see Part 1), re-asked
specifically about catalog. Options offered: leave catalog broken and
proceed / rewrite catalog myself using best judgment / pause everything and
sync with Dev B first.
**Chosen: leave catalog broken, proceed on identity only.**
Reasoning: this is Dev B's feature territory (Post Studio, Publish, Media,
Tags, Admin Tools — Features 7–10, 17) and involves real product decisions
(is admin review gone? what replaces per-post media storage?) that shouldn't
be made unilaterally.

#### Decision 4 — auth foundation (JWT/role derivation) vs. stub
Discovered `SecurityConfig` is a stub and `UserRole` doesn't exist while
scoping Feature 12 (it needs to know "logged in vs. guest" and "does this
reader own an unlock"). Options offered: build the real JWT filter +
`@CurrentUser` + role derivation now, fixing `AuthorProfileServiceImpl` in
the same pass / stub current-user as a request parameter for now and revisit
later.
**Chosen: stub auth for now, revisit later.**
Reasoning: keep moving on the feature itself rather than block it on a
second large, cross-cutting rebuild in the same session. `readerId` is
passed as an explicit `@RequestParam`, which matches the pattern already
used elsewhere in this codebase (`AuthorPostController` takes
`authorProfileId`, `AdminAuthorController` takes `adminUserId`, both as
`@RequestParam` — nobody had wired a security context through yet, even
before this session).

---

## Part 3 — Work done

### Identity module — deletion only
Removed the eight files that implement the dead Feature-5 admin-approval
workflow (`AuthorRequestController`, `AdminAuthorRequestController`,
`AuthorRequestService`, `AuthorRequestServiceImpl`,
`AuthorRequestRepository`, and the three `AuthorRequest*Dto` classes). Left
`AdminAuthorController`/`AdminAuthorService`/`AdminAuthorServiceImpl`
(deactivate/activate author) and `AuthorProfileServiceImpl` untouched and
still broken — their `UserRole` problem is a distinct, undecided design
question (Decision 4 deferred this), not part of what Decision 2 approved.

### Studio module — new, additive contract (fills existing empty scaffolding)
The module already had empty `api/contract` and `api/dto/response` package
declarations (`@NamedInterface` annotations with no classes inside) —
evidence this was always meant to expose something, just never filled in.
Added:
- `WritingContentResponseDto` (`writingId`, `slug`, `priceType` as a plain
  `String` — not the internal `PriceType` enum, to avoid leaking an
  `internal` type across the module boundary — `bodyJson`, `previewJson`).
- `WritingContentService` contract: `getPublishedContentBySlug(String slug)`.
- `WritingContentServiceImpl`: looks up `Writing` by slug, filters to
  `status == PUBLISHED || status == COMPLETED` and `deletedAt == null`,
  throws `EntityNotFoundException` otherwise.

This was necessary because `reading`'s `package-info.java` only declared
`catalog::api-*` and `identity::api-*` as allowed dependencies — but
`catalog.published_writings` doesn't store `body_json` (only `preview_json`
is copied at publish time per `V4__init_catalog.sql`), and there's no
publish/projection service in `studio` yet for reading to lean on instead
(that's Dev B's Feature 7/8 work, left broken per Decision 3). Added
`studio::api-contract` / `studio::api-response-dto` to `reading`'s
`allowedDependencies` so `reading` can query `studio.writings` directly for
full content by slug — a deliberate, documented deviation from the
originally-declared dependency graph (reading → catalog only), justified by
catalog's projection table not carrying the data Feature 12 needs and no
event-driven alternative existing yet.

### Reading module — Feature 12 itself
- `AccessLevel` enum (`PREVIEW`, `FULL`) in `api/contract` — the "shared
  enum in API responses" the feature doc's integration-points section calls
  for.
- `ContentAccessResponseDto` (`slug`, `accessLevel`, `body`) in
  `api/dto/response`.
- `ContentAccessService` contract: `getContent(String slug, Long readerId)`.
- `ContentAccessServiceImpl`:
  - `FREE` post → always `FULL`, body = full `bodyJson`.
  - `LOCKED` post + `ContentAccessRepository.existsByReaderIdAndWritingId`
    true → `FULL`, body = full `bodyJson`.
  - `LOCKED` post, no unlock (including all guests, since `readerId` is
    `null`) → `PREVIEW`, body = explicit `previewJson` if the author set
    one, else a naive 500-character truncation of `bodyJson` as a fallback
    teaser (marked with a `ponytail:` comment — this is a known
    simplification; swap for a proper TipTap/EditorJS-aware truncator if
    guests start seeing broken JSON fragments in the client).
- `ContentAccessController`: `GET /api/v1/posts/{slug}/content?readerId=`
  (optional param). No `SecurityConfig` change needed — `/api/v1/posts/**`
  is already `permitAll()`.
- `ReadingRouteConstant` (`internal/util/constant/`) per the coding
  guideline's controller-routing convention.
- Unit test `ContentAccessServiceImplTest` (plain Mockito, no Spring
  context — the logic has no DB-touching branches worth an integration
  test) covering all four branches above.

---

## Part 4 — Verification

- Ran `./gradlew compileJava` before touching anything: 80 errors, spanning
  catalog (13 files) + identity (4 files, including the since-deleted
  `AuthorRequest*` set).
- After identity deletions: 68 errors — all remaining ones are catalog (13
  files, unchanged, as expected since untouched) + identity's
  `AdminAuthorServiceImpl` and `AuthorProfileServiceImpl` (the `UserRole`
  problem, also unchanged, also expected).
- Explicitly grepped the compiler output for `reading/` and `studio/` after
  adding all new Feature 12 code — zero matches. The new code is clean.
- Ran `./gradlew compileTestJava` (fails for the same pre-existing reasons —
  test compilation depends on main compilation) and confirmed the new test
  file `ContentAccessServiceImplTest.java` produces zero errors of its own.
- **Could not run `./gradlew test`** (including `ModulithVerificationTests`,
  which would confirm the module-boundary changes are actually legal) —
  blocked by the pre-existing catalog/identity compile failures, which are
  out of scope for this session by Decisions 1 and 3. This is the main
  open risk: the `studio::api-contract` dependency addition to `reading`
  is believed correct (mirrors existing patterns like
  `identity::api-contract` being consumed elsewhere) but hasn't been
  confirmed by the actual Modulith verifier yet.

---

## Part 5 — Not done / explicitly deferred

- **Catalog re-architecture** (Decision 3): `CatalogServiceImpl`,
  `AuthorPostServiceImpl`, `AdminPostServiceImpl`, `MediaUploadServiceImpl`,
  `TagServiceImpl` and their DTOs/repositories all still reference deleted
  types. Needs Dev B (or explicit sign-off) on: how publish projects
  `Writing` → `PublishedWriting`, what replaces per-post media storage
  (`PostMedia` has no successor table), whether admin content-review
  (`UNDER_REVIEW`) still exists, and how tags map from `Category` (studio,
  FK-based) to the denormalized string tags in
  `catalog.published_writing_tags`.
- **Role derivation / `UserRole`** (Decision 4): `AdminAuthorServiceImpl`
  and `AuthorProfileServiceImpl` remain broken. Needs a real decision on how
  "derived, non-stored" roles actually compute (e.g., `AUTHOR` = has an
  `AuthorProfile` row — but `ADMIN` has no analog anywhere in the schema).
  `AuthorProfileServiceImpl`'s separate `payoutPhone` field mismatch also
  needs fixing once `UserRole` is resolved.
- **Real JWT filter / `@CurrentUser`** (Decision 4): `SecurityConfig` is
  still a stub. `readerId` is a bare `@RequestParam` on the new content
  endpoint, exactly as trusting as every other "who's calling this" param
  already in the codebase (`authorProfileId`, `adminUserId`) — not a
  regression, but not fixed either.
- **Flyway migration issues** (Part 1): duplicate `V2` version, and
  `V2__seed_tags.sql` inserting into a `catalog.tags` table that no longer
  exists. Not hit yet because the app has never successfully started against
  a real DB since the June 21 refactor.
- **Features 13–16** (Payment & Unlock, Likes, Comments, Reading Progress):
  untouched, as originally scoped — this session was Feature 12 only.

---

## Recommended next steps

1. Get someone (Dev B, or a dedicated session) to rebuild catalog's write
   path against `Writing`/`PublishedWriting`, resolving the admin-review and
   media-storage open questions from Part 5 first, since those are product
   decisions, not mechanical fixes.
2. Decide the role-derivation scheme once, in one place, then fix
   `AuthorProfileServiceImpl` and `AdminAuthorServiceImpl` together — they
   share the same root cause.
3. Once catalog + identity compile again, run `./gradlew test` to confirm
   `ModulithVerificationTests` accepts the new `reading → studio::api-*`
   dependency edge added in this session; adjust if it doesn't.
4. Fix the Flyway `V2` duplicate-version conflict and the orphaned
   `catalog.tags` seed insert before the first real `docker compose up` +
   migrate against a fresh database.
5. Build the real JWT filter + `@CurrentUser` before wiring a frontend to
   the new content-access endpoint, so `readerId` stops being a
   client-supplied, unverified parameter.
