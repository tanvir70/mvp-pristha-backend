# Software Requirements Specification (SRS) — Pristha Digital MVP (v2)

> **Status:** This document supersedes `pristha_srs_mvp.md` for MVP scope.
> It corrects internal contradictions, fills missing schema, and trims the
> feature set to a buildable, hypothesis-validating MVP.
>
> **What changed vs v1** — see the changelog in §0. Anything not in this
> document is explicitly **deferred** (§8), not cancelled.

---

## 0. Changelog & Rationale (v1 → v2)

| # | v1 problem | v2 decision |
|---|-----------|-------------|
| 1 | `ReaderProfile` referenced but no table; `users` had no email/avatar | Reader profile data lives **on `identity.users`** (added `email`, `avatar_url`, `bio`). No separate table for MVP. |
| 2 | JWT carried `roles` but no role storage existed | Roles are **derived, not stored**: every verified user is `READER`; a user with an `author_profile` row is also `AUTHOR`. |
| 3 | `studio` vs `catalog` both claimed authoring | **`studio` owns authoring** (drafts/books/chapters/posts). **`catalog` owns published discovery** (read-only projection). |
| 4 | Cross-schema FKs (e.g. `studio.writings → identity.author_profiles`) broke the "sellable in chunks" goal | **No cross-schema foreign keys.** Cross-module references are plain `BIGINT` soft references; integrity enforced in code/events. Intra-schema FKs are still allowed. |
| 5 | Unlock flow: `billing` had to write `reading`'s table | `reading` **owns** access grants and exposes `grantUnlock(...)` on its contract. `billing` debits the wallet, then calls `reading.grantUnlock()` **synchronously in the same transaction**. |
| 6 | "Double-entry ledger" was a single transfer row; couldn't represent 3-way splits | Real **journal + balanced legs** model (`journal_entries` + `ledger_lines`). |
| 7 | Cached free pages vs per-reader watermark conflicted | Cache the **raw** page; watermark **on egress**. Watermarked output is never cached. |
| 8 | No brute-force protection on OTP/login | OTP attempt cap + resend cooldown + login lockout are **in MVP scope** (§3, NFR-04). |
| 9 | Tenancy mixed RLS / schema-per-tenant / tenant_id column | **One model:** shared schemas + `tenant_id` column + thread-local tenant context. MVP runs as a **single default platform tenant** (`tenant_id = 1`). White-label is deferred. |
| 10 | B2B scouting, rights exchange, escrow, disputes, promos, affiliate, VAT, IP takedown, reviewers, viral-quote, dynamic commission | **Deferred** (§8). Not part of MVP. |

---

## 1. MVP Goal & Scope

**Hypothesis under test:** Bangladeshi writers will paywall long-form/short-form
content, and readers will pay (via wallet credits) to unlock it.

**The one core loop the MVP must prove:**

```
Writer publishes a LOCKED chapter/post
   → Reader loads wallet credits (mock SSLCommerz)
   → Reader one-tap unlocks
   → Reader reads (page-served)
   → Writer's earnings balance increases
```

Everything in this SRS exists only to make that loop work end-to-end and
measure it. If a requirement does not serve that loop, it is in §8 (Deferred).

---

## 2. User Roles (MVP)

* **Guest** — browse public catalog, search, read free previews.
* **Reader** — every verified account. Follow authors, manage library, load
  wallet, unlock and read content.
* **Author** — a Reader who has activated an Author Profile. Can write/publish
  content, set pricing, and view earnings.

> `Moderator` and `B2B Publisher Rep` are **deferred** (§8).
> Roles are **derived** (see §0 #2), not stored in a roles table.

---

## 3. Module Functional Requirements

Module map (MVP): `identity`, `tenant`, `studio`, `catalog`, `reading`,
`billing`, `analytics`. Dependency edges in §5.

### A. Identity Module (`identity`)

#### ID-FR-01: Phone Registration
* **Input:** phone, full name, password, confirm password.
* **Validation:**
  - Phone matches `^(?:\+8801|8801|01)[3-9]\d{8}$`.
  - Password ≥ 8 chars, ≥1 upper, ≥1 lower, ≥1 digit, ≥1 special.
  - Password == confirm password.
* **Processing:**
  - Reject if phone already registered.
  - Hash password with BCrypt.
  - Persist `User` with status `PENDING_VERIFICATION`.
  - Generate a 6-digit OTP, store in Redis `otp:{phone}` (TTL 5 min).
  - (Mock) "send" OTP — log it / return via a dev-only channel.

#### ID-FR-02: Phone OTP Verification (Mock)
* **Input:** phone, 6-digit code.
* **Validation:**
  - User exists and is `PENDING_VERIFICATION`.
  - Code matches `otp:{phone}` in Redis.
  - **Attempt cap:** ≤ 5 failed attempts per OTP (`otp_attempts:{phone}`),
    then invalidate the OTP and require resend.
* **Processing:**
  - Transition status to `VERIFIED`.
  - Delete `otp:{phone}` and `otp_attempts:{phone}`.
  - (No separate reader-profile row — the `User` *is* the reader profile.)

#### ID-FR-02b: Resend OTP
* **Input:** phone.
* **Validation:** resend cooldown of 60s (`otp_resend:{phone}`).
* **Processing:** regenerate OTP, reset TTL and attempt counter.

#### ID-FR-03: Login & Session Generation
* **Input:** phone, password.
* **Validation:**
  - Phone exists and status is `VERIFIED`.
  - BCrypt password match.
  - **Lockout:** after 5 consecutive failures, lock login for 15 min
    (`login_lock:{phone}` in Redis).
* **Processing:**
  - Issue **RS256 JWT access token** (15 min) with claims:
    `userId`, `roles` (derived), `authorProfileId` (nullable), `tenantId`.
  - Issue an opaque **refresh token** (UUID, 30-day), stored in Redis
    `refresh:{userId}:{tokenId}`.
  - Return tokens + basic profile.

#### ID-FR-04: Token Refresh (Rotating)
* **Input:** refresh token.
* **Validation:** token present in Redis and unexpired.
* **Processing:** revoke old refresh token, issue new access + new refresh
  token (rotation).

#### ID-FR-05: Logout
* **Input:** refresh token (and current access token).
* **Processing:** delete the refresh token from Redis.
  > **No access-token blacklist in MVP.** The 15-min access token is allowed
  > to expire naturally; revoking the refresh token stops renewal. (Blacklist
  > deferred — it re-introduces server state and is unnecessary at this TTL.)

#### ID-FR-06: Author Profile Activation
* **Input:** userId, pen name, biography, MFS payout number, MFS provider
  (`BKASH` | `NAGAD` | `ROCKET`).
* **Validation:**
  - Pen name unique.
  - Payout number matches the BD phone format.
* **Processing:**
  - Create `author_profiles` row (intra-schema FK to `users` — allowed).
  - User is now also `AUTHOR` (derived from the row's existence).
  - Create the author's `billing.wallets` row lazily on first earning, or
    eagerly here (implementation choice; see BILL-FR-01).

#### ID-FR-07: Reader Profile Update
* **Input:** userId, full name, email (optional), avatar image (optional), bio.
* **Validation:** email format valid if present; avatar JPEG/PNG ≤ 2 MB.
* **Processing:**
  - Avatar uploaded to MinIO `pristha-public`; store returned URL in
    `users.avatar_url`.
  - Update `full_name`, `email`, `bio`.

#### ID-FR-08: Password Reset (OTP)
* **Input:** phone, OTP, new password.
* **Validation:** OTP matches Redis (same attempt-cap rules as ID-FR-02);
  new password meets ID-FR-01 complexity.
* **Processing:** hash and update; revoke all refresh tokens for the user.

#### `identity::api-contract`
```java
package com.prishtha.mvp.identity.api.contract;

public interface IdentityService {
    UserBasicInfoResponseDto signUp(UserSignUpRequestDto requestDto);
    UserBasicInfoResponseDto verifyOtp(String phone, String code);
    // contract methods used by other modules:
    boolean isUserActive(Long userId);
    boolean isAuthor(Long userId);
    UserBasicInfoResponseDto getUserBasicInfo(Long userId);   // pen name, display name
}
```

---

### B. Tenant Module (`tenant`)

> MVP runs single-tenant. This module exists so content tables carry a real
> `tenant_id` and the context plumbing is in place, but white-label theming
> and custom-domain routing are **deferred** (§8).

#### TEN-FR-01: Default Tenant
* A single seeded platform tenant (`id = 1`, name `Pristha`).
* `TenantContext` (thread-local) is set to `1` for all requests in MVP.

#### `tenant::api-contract`
```java
public interface TenantService {
    boolean existsById(Long tenantId);
}
```

---

### C. Studio Module (`studio`) — Authoring

#### STUDIO-FR-01: Standalone Post Create
* **Input:** authorId, title (optional), body (rich-text JSON), priceType
  (`FREE`|`LOCKED`), priceAmount.
* **Validation:** body non-empty; if `LOCKED`, priceAmount ≥ 1 BDT.
* **Processing:** save `writings` row, `type = POST`, status `DRAFT`,
  under the author's `tenant_id`.

#### STUDIO-FR-02: Post / Content Edit & Soft Delete
* **Validation:** caller owns the writing.
* **Processing:** update fields; soft delete sets `deleted_at`.

#### STUDIO-FR-03: Book Create & Status
* **Input:** authorId, title, synopsis, cover image, tags.
* **Processing:** create `writings` row `type = BOOK`, status `DRAFT`.
* **Statuses:** `DRAFT` (hidden), `UNFINISHED_PREVIEW` (visible, in-progress),
  `COMPLETED`.

#### STUDIO-FR-04: Book Update & Delete
* **Processing:** update metadata; soft delete cascades (soft) to chapters.

#### STUDIO-FR-05: Chapter Create
* **Input:** bookId, authorId, title, orderIndex, body, priceType, priceAmount.
* **Validation:** book exists and belongs to author.
* **Processing:** create `writings` row `type = CHAPTER`, `parent_id = bookId`.

#### STUDIO-FR-06: Chapter Re-order
* **Processing:** remap `order_index` for the supplied chapter IDs in one
  transaction.

#### STUDIO-FR-07: Chapter Edit & Soft Delete
* As STUDIO-FR-02.

#### STUDIO-FR-08: Media Upload (MinIO)
* **Input:** image file from editor.
* **Validation:** ≤ 5 MB; JPEG/PNG/WEBP.
* **Processing:** upload to `pristha-public` under `{tenantId}/uploads/...`;
  return URL.

#### STUDIO-FR-09: Publish
* **Input:** writingId (post or chapter).
* **Validation:** caller owns it; body non-empty.
* **Processing:**
  - Transition status `DRAFT → PUBLISHED`.
  - Publish `ContentPublishedEvent { writingId, tenantId, authorProfileId,
    type, title, priceType, priceAmount, publishedAt }`.

#### `studio::api-contract`
```java
public interface StudioContentService {
    // Body is owned by studio; reading fetches published bodies through this.
    WritingBodyDto getPublishedBody(Long writingId);  // throws if not PUBLISHED
    WritingPricingDto getPricing(Long writingId);      // priceType, priceAmount, authorProfileId
}
```

#### Event
```java
public record ContentPublishedEvent(
    Long writingId, Long tenantId, Long authorProfileId,
    String type, String title, String priceType,
    java.math.BigDecimal priceAmount, java.time.Instant publishedAt) {}
```

---

### D. Catalog Module (`catalog`) — Public Discovery

#### CAT-FR-01: Social Feed
* **Input:** readerId, pageable.
* **Processing:** fetch followed authors → return chronologically sorted
  published posts/chapters projection.

#### CAT-FR-02: Search (PostgreSQL FTS)
* **Input:** query (Bangla/English), optional tag/author filters, pageable.
* **Processing:** query Postgres full-text search over
  `catalog.published_writings`. (Elasticsearch deferred.)

#### CAT-FR-03: Book Detail / Preview
* **Processing:** return book metadata, author pen name, tags, and chapter
  list with `FREE`/`LOCKED` flags. Free chapters readable as preview.

#### CAT-FR-04: Tagging
* **Processing:** link/unlink content to tags at any time (many-to-many).

#### CAT-FR-05: Projection Maintenance (event listener)
* `@ApplicationModuleListener` on `ContentPublishedEvent` upserts
  `catalog.published_writings` (denormalized: stores `author_pen_name`
  resolved via `IdentityService` at index time, since there is no FK).

#### CAT-FR-06: Follow / Unfollow Author
* **Processing:** insert/delete `catalog.follows` (soft reference to author).

#### `catalog::api-contract`
```java
public interface CatalogService {
    PublishedWritingDto getPublishedWriting(Long writingId);
    boolean isPublished(Long writingId);
}
```

---

### E. Reading Module (`reading`) — Delivery & Access

#### READ-FR-01: Access Verification
* **Input:** readerId, writingId.
* **Processing:**
  - If pricing is `FREE` → access granted.
  - Else check `reading.content_access` for `(readerId, writingId)`.
  - No grant → return `CONTENT_LOCKED`.

#### READ-FR-02: Grant Unlock (called by billing)
* Exposed on the contract; **invoked synchronously by `billing`** within the
  unlock transaction.
* **Processing:** insert `content_access (reader_id, writing_id, source =
  PURCHASE)` idempotently (unique constraint).

#### READ-FR-03: Secure Page Server
* **Input:** readerId, writingId, pageNumber.
* **Processing:**
  - Enforce READ-FR-01.
  - Fetch the **raw** body via `StudioContentService.getPublishedBody`
    (cacheable in Redis by `writingId`).
  - Paginate, apply watermark **on egress** (READ-FR-04), return the fragment.

#### READ-FR-04: Watermark on Egress
* Overlay the reader's masked phone/email into the served fragment **at
  response time**. Watermarked output is **never cached**; only the raw body is.
  > Watermarking is a deterrent, not DRM. Do not over-invest. Do **not**
  > embed IP address (privacy). SVG/canvas Bangla CTL rendering deferred —
  > MVP overlay is lightweight HTML/text.

#### READ-FR-05: Library Shelf
* **Processing:** add/remove `reading.library_entries`; list sorted by
  `last_read_at`.

#### READ-FR-06: Reading Progress Save / Resume
* **Processing:** update `last_read_chapter_id`, `last_read_page_num`,
  `last_read_at`; resume returns last coordinates.

#### `reading::api-contract`
```java
public interface ReadingAccessService {
    void grantUnlock(Long readerId, Long writingId);   // idempotent
    boolean hasAccess(Long readerId, Long writingId);
}
```

---

### F. Billing Module (`billing`) — Wallet & Ledger

#### BILL-FR-01: Wallet
* Each user has one `billing.wallets` row (`owner_id` unique). A system
  wallet (`type = SYSTEM_COMMISSION`) collects platform fees.
* Return current balance.

#### BILL-FR-02: Wallet Top-up (Mock SSLCommerz)
* **Input:** userId, amount.
* **Processing (mock):**
  - Create a pending `top_up` request.
  - Simulated callback marks it `SUCCESS`, then credits the wallet via a
    balanced journal (DEBIT external/gateway clearing wallet, CREDIT user
    wallet).
  > **Rule:** the (future real) gateway HTTP call must happen **outside** the
  > DB transaction (NFR-05).

#### BILL-FR-03: Idempotent One-Tap Unlock
* **Input:** readerId, writingId, `Idempotency-Key` header.
* **Validation:**
  - `Idempotency-Key` not already used (authoritative check = unique
    constraint on `journal_entries.idempotency_key`; Redis is an optional
    fast-path, not the source of truth).
  - Content is `LOCKED` and not already unlocked by the reader.
  - Reader wallet balance ≥ price.
* **Processing (single DB transaction):**
  1. Resolve pricing via `StudioContentService.getPricing`.
  2. Post a balanced journal (see BILL-FR-04).
  3. Call `ReadingAccessService.grantUnlock(readerId, writingId)`.
  4. Publish `ContentUnlockedEvent { writingId, readerId }` (async consumers).

#### BILL-FR-04: Immutable Journal Ledger (true multi-leg)
* A unlock posts one `journal_entries` row + ≥3 balanced `ledger_lines`:
  - DEBIT reader wallet `price`
  - CREDIT author wallet `price × authorShare` (default 85%)
  - CREDIT platform wallet `price × platformShare` (default 15%)
* Sum of DEBIT == sum of CREDIT (enforced in code; rows are append-only).
* `journal_entries.idempotency_key` is `UNIQUE`.

#### BILL-FR-05: Author Earnings Overview
* Aggregate the author wallet's ledger lines → total lifetime earnings and
  current balance.

#### BILL-FR-06: Author Withdrawal Request
* **Input:** authorId, amount, target MFS.
* **Validation:** amount ≤ wallet balance.
* **Processing:** post a journal (DEBIT author wallet, CREDIT payout-clearing
  wallet) and create a `payout_requests` row (`PENDING`) for manual processing.

#### `billing::api-contract`
```java
public interface WalletService {
    java.math.BigDecimal getBalance(Long userId);
    boolean hasSufficientBalance(Long userId, java.math.BigDecimal amount);
}
```

#### Event
```java
public record ContentUnlockedEvent(Long writingId, Long readerId) {}
```

---

### G. Analytics Module (`analytics`)

#### AN-FR-01: View Tracking
* `@ApplicationModuleListener` on a `PageViewedEvent` (fired by `reading`) or
  on `ContentUnlockedEvent`.
* Dedup duplicate views with a Redis session hash (1-hour TTL); persist to
  `analytics.content_views`.

#### AN-FR-02: Basic Counts
* Return total view count per writing, and unlock count per writing.

> Completion funnels, scouting score, cohort retention, author dashboards —
> **deferred** (§8).

---

## 4. Database Schema (PostgreSQL 17) — MVP

> **Rules:** one schema per module; `BaseEntity` columns (`id BIGSERIAL`,
> `created_at`, `updated_at`); **no cross-schema FKs** — cross-module links are
> plain `BIGINT` soft references (commented). Tenant-scoped tables carry
> `tenant_id`. Flyway scripts live under `db/migration/{module}/`.

### identity  *(extends existing V1; new migration adds reader fields)*
```sql
-- V3__identity_reader_fields.sql
ALTER TABLE identity.users ADD COLUMN email      VARCHAR(255);
ALTER TABLE identity.users ADD COLUMN avatar_url VARCHAR(512);
ALTER TABLE identity.users ADD COLUMN bio        TEXT;
CREATE UNIQUE INDEX idx_users_email ON identity.users(email) WHERE email IS NOT NULL;
-- users, author_profiles already created in V1 (intra-schema FK kept).
```

### studio
```sql
CREATE SCHEMA IF NOT EXISTS studio;

CREATE TABLE studio.categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE TABLE studio.writings (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,                 -- soft ref → tenant.tenants(id)
    author_id BIGINT NOT NULL,                 -- soft ref → identity.author_profiles(id)
    parent_id BIGINT REFERENCES studio.writings(id),  -- intra-schema FK OK
    title VARCHAR(255),
    body_json JSONB,
    type VARCHAR(30) NOT NULL,                 -- BOOK | CHAPTER | POST
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- DRAFT|UNFINISHED_PREVIEW|PUBLISHED|COMPLETED
    price_type VARCHAR(20) NOT NULL DEFAULT 'FREE', -- FREE | LOCKED
    price_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    order_index INT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX idx_writings_author ON studio.writings(author_id);
CREATE INDEX idx_writings_parent ON studio.writings(parent_id);

CREATE TABLE studio.writing_categories (
    writing_id BIGINT NOT NULL REFERENCES studio.writings(id),
    category_id BIGINT NOT NULL REFERENCES studio.categories(id),
    PRIMARY KEY (writing_id, category_id)
);
```

### catalog
```sql
CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE catalog.published_writings (
    id BIGINT PRIMARY KEY,                      -- = studio.writings(id), soft ref
    tenant_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,                  -- soft ref → identity.author_profiles(id)
    author_pen_name VARCHAR(100) NOT NULL,      -- denormalized at index time
    title VARCHAR(255),
    synopsis TEXT,
    cover_image_url VARCHAR(512),
    type VARCHAR(30) NOT NULL,
    price_type VARCHAR(20) NOT NULL,
    price_amount DECIMAL(10,2) NOT NULL,
    search_tsv TSVECTOR,                        -- Postgres FTS
    published_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX idx_pub_search ON catalog.published_writings USING GIN(search_tsv);

CREATE TABLE catalog.follows (
    id BIGSERIAL PRIMARY KEY,
    follower_id BIGINT NOT NULL,                -- soft ref → identity.users(id)
    author_id BIGINT NOT NULL,                  -- soft ref → identity.author_profiles(id)
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    UNIQUE(follower_id, author_id)
);
```

### reading
```sql
CREATE SCHEMA IF NOT EXISTS reading;

CREATE TABLE reading.content_access (
    id BIGSERIAL PRIMARY KEY,
    reader_id BIGINT NOT NULL,                  -- soft ref → identity.users(id)
    writing_id BIGINT NOT NULL,                 -- soft ref → studio.writings(id)
    source VARCHAR(20) NOT NULL DEFAULT 'PURCHASE', -- PURCHASE | GIFT | FREE
    granted_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    UNIQUE(reader_id, writing_id)
);

CREATE TABLE reading.library_entries (
    id BIGSERIAL PRIMARY KEY,
    reader_id BIGINT NOT NULL,
    writing_id BIGINT NOT NULL,                 -- the BOOK or POST
    last_read_chapter_id BIGINT,
    last_read_page_num INT NOT NULL DEFAULT 1,
    last_read_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    UNIQUE(reader_id, writing_id)
);
```

### billing
```sql
CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.wallets (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL UNIQUE,            -- user id, or NULL-domain system id
    type VARCHAR(30) NOT NULL DEFAULT 'USER',   -- USER | SYSTEM_COMMISSION | CLEARING
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE TABLE billing.journal_entries (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(30) NOT NULL,                  -- TOPUP | UNLOCK | WITHDRAWAL
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE TABLE billing.ledger_lines (
    id BIGSERIAL PRIMARY KEY,
    journal_id BIGINT NOT NULL REFERENCES billing.journal_entries(id),
    wallet_id BIGINT NOT NULL REFERENCES billing.wallets(id),
    direction VARCHAR(6) NOT NULL,             -- DEBIT | CREDIT
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX idx_ledger_wallet ON billing.ledger_lines(wallet_id);

CREATE TABLE billing.payout_requests (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL,                  -- soft ref → identity.author_profiles(id)
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING | PROCESSED | REJECTED
    payout_mfs_number VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
```

### analytics
```sql
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.content_views (
    id BIGSERIAL PRIMARY KEY,
    writing_id BIGINT NOT NULL,                 -- soft ref
    reader_session_hash VARCHAR(255) NOT NULL,
    viewed_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX idx_views_writing ON analytics.content_views(writing_id);
```

---

## 5. Module Dependency Matrix (MVP)

```
                    shared (OPEN)
                       ▲  ▲  ▲
        ┌──────────────┘  │  └──────────────┐
     identity           tenant            (all)
        ▲ ▲                ▲
        │ └──────┐         │
     studio      │      catalog ──(ContentPublishedEvent)──▲ studio
        │        │         ▲
        │        │         │
     reading ────┘ (studio::api-contract, catalog::api-contract)
        ▲
        │ (reading::api-contract)
     billing ──(StudioContentService for pricing)──▲ studio
        │
        ▼ (ContentUnlockedEvent)  ── PageViewedEvent ──▶
     analytics
```

Allowed dependencies (declared in each `package-info.java`):

| Module | allowedDependencies |
|--------|---------------------|
| `shared` | *(OPEN module)* |
| `identity` | `shared` |
| `tenant` | `shared` |
| `studio` | `shared`, `identity::api-contract` |
| `catalog` | `shared`, `identity::api-contract`, `studio::api-event` |
| `reading` | `shared`, `studio::api-contract`, `catalog::api-contract` |
| `billing` | `shared`, `identity::api-contract`, `studio::api-contract`, `reading::api-contract` |
| `analytics` | `shared`, `billing::api-event`, `reading::api-event` |

**No cycles.** Verified by `ModulithVerificationTests`.

---

## 6. Cross-Cutting Rules (carried from audit docs)

* **Encapsulation:** `internal/*` is package-private; cross-module calls only
  through `api/contract/`; never expose `@Entity` via API.
* **Events:** all cross-module side-effects use `@ApplicationModuleListener`
  (async, DB-backed event registry). A failure in `analytics`/`catalog`
  projection must **never** roll back a billing/identity transaction.
* **Synchronous exception:** `billing → reading.grantUnlock()` is intentionally
  synchronous & same-transaction (access must be immediate after payment).
* **MinIO buckets:** `pristha-public` (covers/avatars) vs `pristha-private`
  (paid bodies, deferred — MVP serves bodies from DB JSONB).

---

## 7. Non-Functional Requirements (MVP)

* **NFR-01 Latency:** cache raw published bodies + catalog projection reads in
  Redis (< 50 ms cached). Watermark applied post-cache, on egress.
* **NFR-02 Tenant integrity:** every content/tenant table carries `tenant_id`;
  a tenant-context filter scopes queries. MVP = single tenant `id = 1`.
* **NFR-03 Idempotency:** all wallet/unlock mutations require an
  `Idempotency-Key`; authority is the DB unique constraint on
  `journal_entries.idempotency_key`.
* **NFR-04 Auth abuse resistance:** OTP attempt cap (5), OTP resend cooldown
  (60s), login lockout (5 fails → 15 min).
* **NFR-05 Virtual-thread hygiene:** no HTTP / payment-gateway / SMS calls
  inside `@Transactional`; use `ReentrantLock` / Redis locks, never
  `synchronized`.
* **NFR-06 Money type:** all monetary values are `BigDecimal` /
  `DECIMAL(_,2)`; never `double`/`float`.

---

## 8. Explicitly Deferred (post-MVP)

Kept out of MVP on purpose. Re-introduce after the core loop is validated.

* **Identity:** new-device takeover alert, concurrent-device (2-device) limit,
  access-token blacklist.
* **Studio:** reviewer invitations & private feedback, IP dispute/takedown,
  B2B rights exchange/offers.
* **Catalog:** viral quote image generator, abuse flagging/auto-hide,
  Elasticsearch (use Postgres FTS until enterprise tier).
* **Reading:** SVG/canvas pixel watermarking with Bangla CTL (MVP = light
  text overlay), private-bucket presigned page serving.
* **Billing:** settlement hold/escrow & disputes (BILL-FR-08), promo codes &
  referrals, affiliate split, dynamic commission matrix, VAT/invoice engine,
  daily reconciliation, MFS TxnID self-service lookup, real SSLCommerz.
* **Analytics:** completion funnels, scouting score, cohort retention, author
  dashboard aggregates, B2B scouting feed.
* **Tenant:** white-label theming, custom-domain routing, per-tenant payment
  credentials, multi-tenant onboarding.
* **Roles:** Moderator/Admin console, B2B Publisher Rep.
* **Infra:** Kafka, pgvector semantic search.

---

## 9. Open Product Questions (validate before/while building)

1. **Wallet preload friction.** Forcing a 100 BDT preload to unlock a 5 BDT
   chapter may kill conversion. Consider per-chapter direct MFS purchase, or a
   very low minimum top-up, for first launch.
2. **Unit economics.** Model 15% platform fee minus MFS/gateway fees (~1.5–2%)
   minus VAT (15% on transactional sales): confirm the per-unlock take is
   viable before committing to the 85/15 split.
3. **Piracy/demand.** Paid Bangla long-form reading is the core bet. Watermark
   is a weak deterrent. Validate willingness-to-pay with a small writer cohort
   before scaling DRM investment.
```
