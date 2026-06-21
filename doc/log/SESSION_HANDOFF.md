SESSION-HANDOFF v1 | repo:mvp-pristha-backend | date:2026-06-21
Format: dense/AI-oriented, not prose. Read fully before acting.

CTX: SpringBoot4+Modulith monolith, Postgres17, schema-per-module. Canonical
DB-design source=doc/Design/database_entity_analysis.md (the old blueprint
doc/pristha_database_design_mvp.md was deleted, superseded by it).

== DONE ==

1. DB MIGRATION (commit aac7410 on feature/db-design-migration): rewrote
entities/enums/repos/Flyway V0-V8 to match canonical design, replacing old
Cursor-era schema (flat catalog.posts, single-balance wallet, stored
User.role, admin-gated author_requests).
- identity: UserRole removed (roles derived: ACTIVE status=READER, + a row
  in author_profiles=AUTHOR). UserStatus.VERIFIED->ACTIVE. AuthorRequest
  workflow deleted (open onboarding, no admin gate). AuthorProfile:
  payoutMfsNumber/payoutMfsProvider restored, penName non-null.
- tenant: white-label (TenantDomain/TenantTheme/TenantInterceptor/
  TenantContext/WebMvcConfig) deleted entirely, simplified to single-tenant
  (tenant.tenants, id=1 seed "Pristha"). TenantService reduced to
  existsById(Long).
- studio: NEW module. Writing (book/chapter/post unified via `type` enum,
  self-FK `parent`, JSONB bodyJson/previewJson, enums type/status/priceType,
  priceAmount, orderIndex, deletedAt soft-delete), Category, WritingCategory
  composite-key join.
- catalog: Post/PostMedia/PostTag/Tag deleted -> PublishedWriting (assigned
  PK = studio.writings.id, no @GeneratedValue; mirrored type/status/
  priceType as plain String not enums — Spring Modulith blocks reaching
  into studio's internal enums regardless of allowedDependencies; generated
  STORED search_tsv FTS column, GIN indexed, never mapped as writable
  field), PublishedWritingTag, Follow.
- social: PostLike/PostComment -> WritingLike/WritingComment (self-FK
  parent_id for reply threads).
- reading: ReadingProgress -> ContentAccess (source enum PURCHASE/GIFT/
  FREE) + LibraryEntry (last-read chapter/page position).
- billing: single Wallet+PaymentTransaction -> true double-entry ledger:
  Wallet (type enum USER/SYSTEM_COMMISSION/CLEARING, unique
  (ownerId,type)), TopUpRequest, JournalEntry (idempotencyKey unique,
  guards against double-processing), LedgerLine (DEBIT/CREDIT, FK to
  journal+wallet, append-only), PayoutRequest. Seeded 2 system wallets at
  owner_id=0 (SYSTEM_COMMISSION, CLEARING).
- analytics: NEW module. ContentView, ContentUnlock — dedup view/unlock
  logs, decoupled from the billing txn (event-driven per doc).
- shared: V0__shared_functions.sql adds shared.set_updated_at() Postgres
  trigger fn, attached via CREATE TRIGGER to every table with BOTH
  created_at+updated_at NOT NULL (doc mandates trigger-driven updated_at,
  not just Java). BaseEntity's @PreUpdate left in place — redundant but
  harmless, trigger overwrites it again on the real UPDATE.
- Entity-modeling rule applied everywhere: extend BaseEntity only if a
  table has BOTH timestamps NOT NULL; else plain @Id @GeneratedValue(
  IDENTITY) + manual @PrePersist. Cross-schema references stay plain Long
  fields, never @ManyToOne/@JoinColumn (only intra-schema FKs get real JPA
  relations). A column gets a Java enum only if CHECK-constrained by the
  module that owns it; columns that merely mirror another module's value
  stay String.
- Verified: ./gradlew compileJava/compileTestJava clean (no leftover refs
  to deleted types). Local `mvp` Postgres DB was empty, Flyway applied
  V0-V8 fresh. spring.jpa.hibernate.ddl-auto=validate passed at startup
  (entities match schema exactly). ./gradlew test: 5/5 green
  (MvpApplicationTests.contextLoads, ModulithVerificationTests,
  IdentityServiceIntegrationTests signup+OTP now asserting ACTIVE).

2. DOCKER SECRETS (commit 5a663f2): compose.yaml had a hardcoded
placeholder Postgres password ("secret") that didn't even match real local
creds. Fixed: POSTGRES_DB/POSTGRES_USER/POSTGRES_PASSWORD now read from
env vars sourced via `.env` (gitignored), with Compose `:?` syntax to
hard-fail if password missing. `.env.example` committed with placeholder
`changeme` (real `.env` has actual local creds, never committed).
application.properties datasource settings switched to
${SPRING_DATASOURCE_URL/USERNAME/PASSWORD:fallback} so they're env-
overridable for prod (Render deploy via existing Dockerfile) while still
working standalone for local dev. Host port remapped 5432->5433 in
compose.yaml after discovering the user's native local Postgres install
already occupies host 5432 (container-internal port unchanged at 5432;
spring-boot-docker-compose dev dependency auto-discovers the actual
published port by inspecting the container, so nothing else needed
updating). Run via `./gradlew bootRun` — spring-boot-docker-compose
(developmentOnly dep) auto-starts compose.yaml and auto-wires the
datasource from it; no manual `docker compose up` required for plain app
runs (works from IDE run configs too, excluded from packaged jar).

3. DOC CLEANUP (commit 198a59f): merged
doc/architectural_audit_checklist_v2.md into the main checklist (now
doc/Design/architectural_audit_checklist.md) as new sections 5-7; flagged
its old §4 (white-label tenancy) as "deferred for MVP" rather than leaving
it silently contradicting the already-simplified single-tenant code.
Deleted doc/db-design-mvp.md (old superseded Cursor-era design) and
doc/pristha_database_design_mvp.md (the canonical blueprint used
throughout migration #1 — deleted now that implementation matches it
exactly) in favor of doc/Design/database_entity_analysis.md as the sole,
current DB-design source of truth. Deleted doc/task/mvp-feature-
breakdown.md — its scope/links were entirely anchored on the old
posts-only design, stale beyond repair via a simple edit. Migration log
renamed doc/log/migration_log_db_design_alignment.md ->
doc/log/2026-06-20-db-migration-docker-and-doc-cleanup.md, extended with
parts 2 (docker) and 3 (this doc cleanup).

4. GIT STATE: the 3 commits above were originally made directly on
`develop`, then moved off it per user request: created branch
`feature/db-design-migration` at that point, then `git reset --hard
origin/develop` on develop (now back in exact sync with origin, 0 ahead/
behind). `feature/db-design-migration` pushed to origin with upstream
tracking set. NO PR opened yet (GitHub gave the create-PR URL on push, not
used). User was left checked out on `develop` (post-reset, clean).

5. PLUGIN: installed DietrichGebert/ponytail (a "lazy senior dev" code-
minimization skill/plugin for Claude Code — ladder: YAGNI -> stdlib ->
native -> one line -> minimum) via `/plugin marketplace add
DietrichGebert/ponytail` + `/plugin install ponytail@ponytail`. Was NOT
active in that session (plugins load on Claude Code restart or
`/reload-plugins`). Once active: `/ponytail` (default=full),
`/ponytail lite`, `/ponytail ultra`, `/ponytail-review` (over-engineering
diff review), `/ponytail-gain` (impact scoreboard), `/ponytail-help`
(reference card). Deactivate: "stop ponytail" or `/ponytail off`.

== QUIRKS / OPERATING NOTES ==

- User pair-programs with "Antigravity" (a separate AI tool) concurrently
  on this same repo. Unexplained file edits, staged-but-unfamiliar
  changes, or doc reorganization (e.g. files moving into doc/Design/,
  doc/task/, doc/bussiness/ subfolders) are EXPECTED — do not flag as
  suspicious or ask "was this intended," the user is already aware.
- .env.example briefly mirrored real local creds (edited externally),
  ended up back at placeholder `changeme` in the committed state — that's
  correct/fine, don't "fix" it again unless it drifts from placeholder
  values in a way that actually leaks something real.
- The agent sandbox used for this work had no Docker daemon running — full
  container boot was never verified end-to-end, only `docker compose
  config` resolution. Confirm `docker compose up` / `./gradlew bootRun`
  actually connects on a real machine if not already done.

== PENDING / NOT DONE ==

- No PR yet for feature/db-design-migration -> develop.
- No new services/controllers/DTOs for studio/catalog/social/reading/
  billing/analytics modules — only `identity` has a working HTTP surface
  today (signup + OTP verify). This was a deliberate scope boundary for
  the migration, not an oversight.
- No unlock-transaction service implemented (the 3-leg ledger transaction
  described in doc §9 commentary: debit reader wallet, credit author
  wallet 85%, credit SYSTEM_COMMISSION wallet 15%) — only the data model
  exists, the transaction logic is future work.
- doc/task/mvp-feature-breakdown.md was deleted for being stale; no
  replacement breakdown doc has been written for the new design yet.

== HOW TO RESUME ==

1. `git fetch origin && git checkout feature/db-design-migration` (or
   `develop` if starting something unrelated to this migration).
2. Read doc/log/2026-06-20-db-migration-docker-and-doc-cleanup.md (full
   rationale/decisions) and doc/Design/database_entity_analysis.md (current
   DB design) before making further schema or entity changes.
3. If continuing toward a PR: feature/db-design-migration is pushed and
   ready, just needs `gh pr create` (or the GitHub URL from the push
   output) once the user confirms they want to open it.
