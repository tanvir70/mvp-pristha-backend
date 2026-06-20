# Engineering Log — DB Design Migration, Docker Secrets, Doc Cleanup

**Date:** 2026-06-20
**Trigger:** Codebase had drifted onto a different, earlier (Cursor-authored)
schema instead of our own canonical design. Discovered when new repositories
were being added against the *existing* (wrong) entities instead of being
reconciled against the canonical doc. Decision: migrate the code to match
the doc, not the reverse, and not a hybrid.

Scope: entities, enums, repositories, and Flyway migrations only. No new
services, controllers, DTOs, or event listeners were invented — only
`identity` had real service/controller logic going in; the rest had only
entities + repositories (or nothing, for `studio`/`analytics`), so this was
kept a pure data-layer rewrite.

---

## Part 1 — DB design migration

### Decisions made

#### 1. Tenant white-label routing — removed
`TenantDomain`, `TenantTheme`, `TenantInterceptor`, `WebMvcConfig`,
`TenantContext` were fully wired in code but the canonical doc explicitly
defers white-label domains/themes for the MVP. Asked the user; chose to
simplify to single-tenant (`tenant.tenants`, id = 1) rather than keep
out-of-scope infrastructure alive.

#### 2. Flyway strategy — rewrite in place, not append
No production data exists yet (pre-launch), so existing `V1`/`V2` migration
files were edited/replaced directly and renumbered to the doc's global
`V0`–`V8` ordering, instead of appending new versioned migrations on top of
the wrong schema. Asked the user; confirmed rewrite-in-place.

#### 3. `updated_at` ownership — DB trigger, not just Java
The doc specifies `updated_at` is maintained by a Postgres trigger
(`shared.set_updated_at`), while the existing code only managed timestamps
in Java (`BaseEntity` `@PrePersist`/`@PreUpdate`). Rather than silently
picking one (the mistake that caused the original drift), implemented the
trigger exactly as the doc specifies in a new `V0__shared_functions.sql`,
on every table with both `created_at`/`updated_at`. Left `BaseEntity`
untouched — its `@PreUpdate` becomes redundant (trigger overwrites
`updated_at` again on the real `UPDATE`) but harmless.

#### 4. Entity-modeling rule (applied consistently across all modules)
- Extend `BaseEntity` only when a table has **both** `created_at NOT NULL`
  and `updated_at NOT NULL`. Tables with only one timestamp (or none) get a
  plain `@Id @GeneratedValue(IDENTITY)` entity with manual `@PrePersist`.
- `catalog.published_writings.id` is an **assigned** PK (copied from
  `studio.writings.id` at publish time) — `@Id` with no `@GeneratedValue`.
- Cross-schema references stay plain `Long` fields, no `@ManyToOne`/
  `@JoinColumn` — only intra-schema FKs (e.g. self-joins, `billing.ledger_lines`
  → `journal_entries`/`wallets`) get real JPA relationships.
- A column gets a Java enum only if it's `CHECK`-constrained in the module
  that *owns* it (e.g. `studio.writings.type/status`). Columns that merely
  *mirror* another module's validated value (e.g.
  `catalog.published_writings.type/status/price_type`, copied from
  `studio.writings` but not independently checked in catalog's own SQL)
  stay plain `String` — both to match the doc exactly and because Spring
  Modulith would block reaching into another module's `internal.enums`
  package regardless of `allowedDependencies`.

### Work done, by phase

| Phase | Module | Action |
|---|---|---|
| 0 | `shared` | New `V0__shared_functions.sql` — `shared.set_updated_at()` trigger function. |
| 1 | `identity` | Removed stored `role` (`UserRole` enum deleted — roles are derived, not stored). Removed admin-gated `AuthorRequest` workflow entirely (open author onboarding per doc). Renamed `UserStatus.VERIFIED` → `ACTIVE`. Restored `payout_mfs_number`/`payout_mfs_provider` on `AuthorProfile`. Rewrote `V1__init_identity.sql`. |
| 2 | `tenant` | Deleted white-label routing infra (see decision #1). `TenantService` reduced to `existsById`. Rewrote `V2__init_tenant.sql` — single `tenants` table, seeded id=1 "Pristha". |
| 3 | `studio` | **New module.** `Writing` (book/chapter/post unified, self-referencing `parent_id`, `bodyJson`/`previewJson` as JSONB, `type`/`status`/`priceType` enums), `Category`, `WritingCategory` join. `V3__init_studio.sql`. |
| 4 | `catalog` | Replaced flat `Post`/`PostMedia`/`PostTag`/`Tag` model with `PublishedWriting` — a read-optimized, denormalized projection of `studio.writings` (assigned PK, mirrored `String` status fields, generated `search_tsv` full-text column). Added `Follow`. `V4__init_catalog.sql`. |
| 5 | `social` | Replaced post-keyed `PostLike`/`PostComment` with writing-keyed `WritingLike`/`WritingComment` (self-referencing `parent_id` for reply threads). `V5__init_social.sql`. |
| 6 | `reading` | Replaced `ReadingProgress` (scroll-position tracking) with `ContentAccess` (purchase/gift/free grants) + `LibraryEntry` (last-read position per reader/writing). `V6__init_reading.sql`. |
| 7 | `billing` | Replaced single-balance `Wallet` + `PaymentTransaction` with a true double-entry ledger: `Wallet` (typed: USER/SYSTEM_COMMISSION/CLEARING), `TopUpRequest`, `JournalEntry` (idempotency-keyed transaction header), `LedgerLine` (append-only DEBIT/CREDIT legs), `PayoutRequest`. Seeded two system wallets (`owner_id = 0`). `V7__init_billing.sql`. |
| 8 | `analytics` | **New module.** `ContentView` (deduplicated view log), `ContentUnlock` (decoupled unlock log for async analysis). `V8__init_analytics.sql`. |
| 9 | wiring | `application.properties` `spring.flyway.locations` updated to include `shared`, `studio`, `analytics` in doc order (`shared→identity→tenant→studio→catalog→social→reading→billing→analytics`). |

### Verification

- `./gradlew compileJava` / `compileTestJava` — clean, no leftover references
  to deleted types (`UserRole`, `UserStatus.VERIFIED`, `TenantContext`,
  `TenantDomain`/`TenantTheme`, old `Post`/`Wallet`/`ReadingProgress`, etc.).
- Local `mvp` Postgres database was empty (no prior schemas) — Flyway
  applied all migrations V0–V8 fresh, no destructive drop needed.
- `spring.jpa.hibernate.ddl-auto=validate` passed at context startup —
  every entity matches its migration schema exactly.
- `./gradlew test` — 5/5 passing:
  - `MvpApplicationTests.contextLoads()`
  - `ModulithVerificationTests` (module boundary structure + docs)
  - `IdentityServiceIntegrationTests` (signup + OTP verify flow, now
    asserting `ACTIVE` status)

### Not done (explicitly out of scope for this pass)

- No new services/controllers/DTOs for `studio`, `catalog`, `social`,
  `reading`, `billing`, `analytics` — only `identity` has a working HTTP
  surface today.
- No unlock-transaction service (the 3-leg ledger transaction described in
  the doc's §9 commentary) — only the data model exists; the txn logic is
  future work.
- No manual end-to-end click-through beyond the existing identity
  signup/OTP endpoints (the only live HTTP flow).

---

## Part 2 — Docker secrets setup

**Trigger:** project already had a `compose.yaml` (Spring Boot Docker
Compose support, `developmentOnly` dependency in `build.gradle`) and a
`Dockerfile`, but the compose file hardcoded a placeholder Postgres
password (`secret`) that didn't even match the app's real local credentials.

### Decisions made
- Kept the existing `compose.yaml` instead of adding a second, redundant
  compose file — `spring-boot-docker-compose` auto-starts it and auto-wires
  the datasource from the running container, so no manual `spring.datasource.*`
  is required for local dev.
- Switched `compose.yaml` to read `POSTGRES_DB`/`POSTGRES_USER`/
  `POSTGRES_PASSWORD` from environment variables (via `.env`), with `:?`
  Compose syntax to hard-fail if the password is missing, instead of a
  literal secret committed to the file.
- Added `.env` (gitignored, real local values) + `.env.example` (committed
  template) and added `.env` to `.gitignore`.
- `application.properties` `spring.datasource.*` switched to
  `${SPRING_DATASOURCE_URL/USERNAME/PASSWORD:...}` placeholders with the old
  values as local-dev fallback defaults — lets the production `Dockerfile`
  deploy (Render) inject real secrets via env vars instead.
- Container's published Postgres port remapped from host `5432` → `5433`
  (kept container-internal port at `5432`) after discovering the user's
  native local Postgres install already occupies host port `5432`. Spring
  Boot's docker-compose support discovers the actual published port by
  inspecting the container, so no other config needed updating.

### Verification
- `docker compose config` resolves `POSTGRES_DB=mvp`/`POSTGRES_USER=postgres`/
  `POSTGRES_PASSWORD=psql` correctly from `.env` (later changed by the user
  to `POSTGRES_DB=pristha`).
- Could not fully boot the container in the agent sandbox (no Docker daemon
  there) — confirmed config resolution only; user verifies actual `docker
  compose up` / `./gradlew bootRun` on their machine.

---

## Part 3 — Documentation cleanup

- Merged `doc/architectural_audit_checklist_v2.md` into
  `doc/architectural_audit_checklist.md` (now `doc/Design/architectural_audit_checklist.md`)
  as sections 5–7; deleted the `_v2` file. Flagged §4 (white-label tenancy)
  as deferred-for-MVP rather than silently leaving it contradicting the
  already-simplified single-tenant code.
- Deleted `doc/db-design-mvp.md` (old, superseded Cursor-style design) and
  `doc/pristha_database_design_mvp.md` (the canonical design blueprint used
  throughout Part 1) in favor of `doc/database_entity_analysis.md` as the
  single, current source of truth for the data model — now that the
  implementation matches the design exactly, the as-built entity dictionary
  supersedes the original blueprint.
- Repointed the dangling citation this left in
  `doc/Design/architectural_audit_checklist.md` (§4) from
  `pristha_database_design_mvp.md` to `database_entity_analysis.md`.
- **Flagged, not fixed:** `doc/task/mvp-feature-breakdown.md` still links to
  the now-deleted `db-design-mvp.md` twice and its scope description is
  framed entirely around the old "posts-only" design — stale beyond just a
  broken link, pending a separate decision on whether to update or retire it.
