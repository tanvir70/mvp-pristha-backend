# Migration Log — Codebase Alignment to `pristha_database_design_mvp.md`

**Date:** 2026-06-20
**Trigger:** Codebase had drifted onto a different, earlier (Cursor-authored)
schema (`doc/db-design-mvp.md`) instead of our own canonical design
(`doc/pristha_database_design_mvp.md`). Discovered when new repositories
were being added against the *existing* (wrong) entities instead of being
reconciled against the canonical doc. Decision: migrate the code to match
the doc, not the reverse, and not a hybrid.

Scope: entities, enums, repositories, and Flyway migrations only. No new
services, controllers, DTOs, or event listeners were invented — only
`identity` had real service/controller logic going in; the rest had only
entities + repositories (or nothing, for `studio`/`analytics`), so this was
kept a pure data-layer rewrite.

---

## Decisions made

### 1. Tenant white-label routing — removed
`TenantDomain`, `TenantTheme`, `TenantInterceptor`, `WebMvcConfig`,
`TenantContext` were fully wired in code but the canonical doc explicitly
defers white-label domains/themes for the MVP. Asked the user; chose to
simplify to single-tenant (`tenant.tenants`, id = 1) rather than keep
out-of-scope infrastructure alive.

### 2. Flyway strategy — rewrite in place, not append
No production data exists yet (pre-launch), so existing `V1`/`V2` migration
files were edited/replaced directly and renumbered to the doc's global
`V0`–`V8` ordering, instead of appending new versioned migrations on top of
the wrong schema. Asked the user; confirmed rewrite-in-place.

### 3. `updated_at` ownership — DB trigger, not just Java
The doc specifies `updated_at` is maintained by a Postgres trigger
(`shared.set_updated_at`), while the existing code only managed timestamps
in Java (`BaseEntity` `@PrePersist`/`@PreUpdate`). Rather than silently
picking one (the mistake that caused the original drift), implemented the
trigger exactly as the doc specifies in a new `V0__shared_functions.sql`,
on every table with both `created_at`/`updated_at`. Left `BaseEntity`
untouched — its `@PreUpdate` becomes redundant (trigger overwrites
`updated_at` again on the real `UPDATE`) but harmless.

### 4. Entity-modeling rule (applied consistently across all modules)
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

---

## Work done, by phase

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

---

## Verification

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

## Not done (explicitly out of scope for this pass)

- No new services/controllers/DTOs for `studio`, `catalog`, `social`,
  `reading`, `billing`, `analytics` — only `identity` has a working HTTP
  surface today.
- No unlock-transaction service (the 3-leg ledger transaction described in
  the doc's §9 commentary) — only the data model exists; the txn logic is
  future work.
- No manual end-to-end click-through beyond the existing identity
  signup/OTP endpoints (the only live HTTP flow).
