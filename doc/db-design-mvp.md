# Prokash Digital — MVP Database Design

PostgreSQL schema design for the first MVP · Module-aligned · June 2025

> **Scope:** Public catalog with free and paid posts, preview for locked content, login + payment unlock, like/comment gated by full read access, admin-approved author onboarding, and document-style rich-text posts.
>
> **Stack alignment:** Schemas per Spring Modulith module (`identity`, `catalog`, `billing`, `reading`, `social`). Sessions, OTP, and idempotency keys live in Redis — not persisted here.

---

## 1. Design Principles

- **Schema per module** — Tables grouped under PostgreSQL schemas matching bounded contexts.
- **UUID primary keys** — Safe for distributed IDs and public API exposure.
- **Soft delete** — `deleted_at` on user-facing content; hard delete avoided for audit.
- **Preview vs full body** — Locked posts store `preview_body` (public) and `body` (full, paywalled).
- **Access = purchase OR free** — Application checks `pricing_type` and `content_unlocks` before like/comment/full read.

### Business rules

- **Guest:** browse catalog, read free posts fully, read locked post *preview* only.
- **Logged-in reader:** like/comment on **free** posts and **purchased** locked posts only.
- **Author:** created only after `author_requests` is `APPROVED` by admin.
- Like/comment tables use unique constraints; service layer rejects when user lacks full read access.

---

## 2. Entity Relationship Overview

```
identity.users (avatar, bio, …) ──┬──── identity.author_profiles
                                  │              │
                                  │    identity.author_requests (PENDING → APPROVED)
                                  │
                                  ├──── catalog.posts (author_id, pricing_type, preview_body, body)
                                  │         ├── catalog.post_tags ── catalog.tags
                                  │         └── catalog.post_media
                                  │
                                  ├──── social.post_likes
                                  ├──── social.post_comments
                                  │
                                  ├──── billing.wallets
                                  ├──── billing.payment_transactions ── billing.content_unlocks
                                  │
                                  └──── reading.reading_progress
```

---

## 3. Schema: `identity`

Authentication, unified user profile, and author onboarding (admin approval required). Reader-facing fields live on `users` — no separate reader profile table.

### 3.1 `users`


| Column        | Type          | Constraints                              | Description                               |
| ------------- | ------------- | ---------------------------------------- | ----------------------------------------- |
| **id**        | UUID          | PK                                       | Primary key                               |
| phone         | VARCHAR(15)   | UNIQUE, NOT NULL                         | BD format: +8801… / 01…                   |
| password_hash | VARCHAR(255)  | NOT NULL                                 | BCrypt hash                               |
| full_name     | VARCHAR(120)  | NOT NULL                                 | Display name at registration              |
| email         | VARCHAR(255)  | UNIQUE, NULL                             | Optional, set in profile update           |
| avatar_url    | VARCHAR(512)  | NULL                                     | Profile photo (MinIO / CDN URL)           |
| bio           | TEXT          | NULL                                     | Short user bio                            |
| status        | `user_status` | NOT NULL, DEFAULT `PENDING_VERIFICATION` | PENDING_VERIFICATION, VERIFIED, SUSPENDED |
| role          | `user_role`   | NOT NULL, DEFAULT `READER`               | READER, AUTHOR, ADMIN                     |
| created_at    | TIMESTAMPTZ   | NOT NULL                                 | Row created                               |
| updated_at    | TIMESTAMPTZ   | NOT NULL                                 | Last update                               |


### 3.2 `author_profiles`


| Column       | Type        | Constraints                     | Description                                                      |
| ------------ | ----------- | ------------------------------- | ---------------------------------------------------------------- |
| **id**       | UUID        | PK                              | Primary key                                                      |
| user_id      | UUID        | FK → users.id, UNIQUE, NOT NULL | Linked verified user                                             |
| pen_name     | VARCHAR(80) | UNIQUE, NULL                    | Optional public author name; if NULL, display `users.full_name`  |
| biography    | TEXT        | NULL                            | Author about text                                                |
| payout_phone | VARCHAR(15) | NULL                            | bKash / Nagad for future payouts                                 |
| is_active    | BOOLEAN     | NOT NULL, DEFAULT true          | Admin can deactivate                                             |
| created_at   | TIMESTAMPTZ | NOT NULL                        | Row created                                                      |
| updated_at   | TIMESTAMPTZ | NOT NULL                        | Last update                                                      |


### 3.3 `author_requests`


| Column             | Type                    | Constraints                 | Description                      |
| ------------------ | ----------------------- | --------------------------- | -------------------------------- |
| **id**             | UUID                    | PK                          | Primary key                      |
| user_id            | UUID                    | FK → users.id, NOT NULL     | Applicant                        |
| requested_pen_name | VARCHAR(80)             | NULL                        | Optional desired pen name        |
| motivation         | TEXT                    | NULL                        | Optional — why they want to write |
| sample_writing_url | VARCHAR(512)            | NULL                        | Optional portfolio link          |
| status             | `author_request_status` | NOT NULL, DEFAULT `PENDING` | PENDING, APPROVED, REJECTED      |
| reviewed_by        | UUID                    | FK → users.id, NULL         | Admin who decided                |
| review_note        | TEXT                    | NULL                        | Rejection reason / internal note |
| reviewed_at        | TIMESTAMPTZ             | NULL                        | When admin acted                 |
| created_at         | TIMESTAMPTZ             | NOT NULL                    | Request submitted                |


*Partial unique index: one `PENDING` request per `user_id`.*

---

## 4. Schema: `catalog`

Posts with document-style rich text (TipTap / Editor.js JSON), free vs locked pricing, and tags.

### 4.1 `posts`


| Column          | Type           | Constraints                       | Description                                                          |
| --------------- | -------------- | --------------------------------- | -------------------------------------------------------------------- |
| **id**          | UUID           | PK                                | Primary key                                                          |
| author_id       | UUID           | FK → author_profiles.id, NOT NULL | Post owner                                                           |
| title           | VARCHAR(255)   | NULL                              | Optional headline                                                    |
| slug            | VARCHAR(280)   | UNIQUE, NOT NULL                  | URL-friendly identifier                                              |
| excerpt         | VARCHAR(500)   | NULL                              | Card / feed summary                                                  |
| body            | JSONB          | NOT NULL                          | Full rich-text document (Editor.js / TipTap)                         |
| body_plain_text | TEXT           | NULL                              | Denormalized for search (MVP: SQL LIKE; later Elasticsearch)         |
| preview_body    | JSONB          | NULL                              | Teaser when LOCKED and not purchased; NULL ⇒ auto-truncate from body |
| cover_image_url | VARCHAR(512)   | NULL                              | Hero / OG image                                                      |
| pricing_type    | `pricing_type` | NOT NULL, DEFAULT `FREE`          | FREE, LOCKED                                                         |
| price_amount    | DECIMAL(10,2)  | NULL                              | BDT; required when LOCKED (≥ 1.00)                                   |
| status          | `post_status`  | NOT NULL, DEFAULT `DRAFT`         | DRAFT, PUBLISHED, UNDER_REVIEW                                       |
| published_at    | TIMESTAMPTZ    | NULL                              | Set when status → PUBLISHED                                          |
| view_count      | BIGINT         | NOT NULL, DEFAULT 0               | Aggregated views (Redis buffer optional)                             |
| like_count      | INT            | NOT NULL, DEFAULT 0               | Denormalized from post_likes                                         |
| comment_count   | INT            | NOT NULL, DEFAULT 0               | Denormalized from post_comments                                      |
| deleted_at      | TIMESTAMPTZ    | NULL                              | Soft delete                                                          |
| created_at      | TIMESTAMPTZ    | NOT NULL                          | Row created                                                          |
| updated_at      | TIMESTAMPTZ    | NOT NULL                          | Last update                                                          |


*CHECK: `pricing_type = 'LOCKED'` ⇒ `price_amount >= 1`; `FREE` ⇒ `price_amount IS NULL`.*

### 4.2 `tags` & `post_tags`


| Table / Column    | Type        | Constraints                   | Description          |
| ----------------- | ----------- | ----------------------------- | -------------------- |
| tags.id           | UUID        | PK                            | Tag primary key      |
| tags.name         | VARCHAR(60) | UNIQUE, NOT NULL              | e.g. Fiction, Poetry |
| tags.slug         | VARCHAR(70) | UNIQUE, NOT NULL              | URL slug             |
| post_tags.post_id | UUID        | FK → posts.id, PK (composite) | Many-to-many link    |
| post_tags.tag_id  | UUID        | FK → tags.id, PK (composite)  | Many-to-many link    |


### 4.3 `post_media`


| Column          | Type         | Constraints                       | Description                    |
| --------------- | ------------ | --------------------------------- | ------------------------------ |
| **id**          | UUID         | PK                                | Primary key                    |
| post_id         | UUID         | FK → posts.id, NULL               | NULL while uploading in editor |
| author_id       | UUID         | FK → author_profiles.id, NOT NULL | Uploader                       |
| storage_key     | VARCHAR(512) | NOT NULL                          | MinIO object path              |
| mime_type       | VARCHAR(80)  | NOT NULL                          | image/jpeg, etc.               |
| file_size_bytes | INT          | NOT NULL                          | Max 5MB enforced in API        |
| created_at      | TIMESTAMPTZ  | NOT NULL                          | Upload time                    |


---

## 5. Schema: `social`

Likes and comments — API must verify full read access before insert.

### 5.1 `post_likes`


| Column     | Type        | Constraints             | Description      |
| ---------- | ----------- | ----------------------- | ---------------- |
| **id**     | UUID        | PK                      | Primary key      |
| post_id    | UUID        | FK → posts.id, NOT NULL | Liked post       |
| user_id    | UUID        | FK → users.id, NOT NULL | Reader who liked |
| created_at | TIMESTAMPTZ | NOT NULL                | Like timestamp   |


*UNIQUE (post_id, user_id). Allowed only if post is FREE or user has `content_unlocks` row.*

### 5.2 `post_comments`


| Column     | Type        | Constraints                 | Description                 |
| ---------- | ----------- | --------------------------- | --------------------------- |
| **id**     | UUID        | PK                          | Primary key                 |
| post_id    | UUID        | FK → posts.id, NOT NULL     | Commented post              |
| user_id    | UUID        | FK → users.id, NOT NULL     | Comment author              |
| parent_id  | UUID        | FK → post_comments.id, NULL | Thread reply (optional MVP) |
| body       | TEXT        | NOT NULL                    | Plain-text comment          |
| deleted_at | TIMESTAMPTZ | NULL                        | Soft delete                 |
| created_at | TIMESTAMPTZ | NOT NULL                    | Comment time                |
| updated_at | TIMESTAMPTZ | NOT NULL                    | Last edit                   |


---

## 6. Schema: `billing`

Wallet, SSLCommerz payments, and purchase records that grant full content access.

### 6.1 `wallets`


| Column     | Type          | Constraints                     | Description                                          |
| ---------- | ------------- | ------------------------------- | ---------------------------------------------------- |
| **id**     | UUID          | PK                              | Primary key                                          |
| user_id    | UUID          | FK → users.id, UNIQUE, NOT NULL | One wallet per user                                  |
| balance    | DECIMAL(12,2) | NOT NULL, DEFAULT 0             | Spendable BDT (optional MVP; direct gateway also OK) |
| currency   | CHAR(3)       | NOT NULL, DEFAULT `BDT`         | ISO currency                                         |
| updated_at | TIMESTAMPTZ   | NOT NULL                        | Last balance change                                  |


### 6.2 `payment_transactions`


| Column          | Type             | Constraints                    | Description                        |
| --------------- | ---------------- | ------------------------------ | ---------------------------------- |
| **id**          | UUID             | PK                             | Primary key                        |
| user_id         | UUID             | FK → users.id, NOT NULL        | Payer                              |
| post_id         | UUID             | FK → posts.id, NOT NULL        | Content purchased                  |
| amount          | DECIMAL(10,2)    | NOT NULL                       | Charged BDT                        |
| gateway         | VARCHAR(30)      | NOT NULL, DEFAULT `SSLCOMMERZ` | Payment provider                   |
| gateway_txn_id  | VARCHAR(120)     | UNIQUE, NULL                   | SSLCommerz tran_id                 |
| idempotency_key | VARCHAR(64)      | UNIQUE, NOT NULL               | Client header; duplicate guard     |
| status          | `payment_status` | NOT NULL, DEFAULT `PENDING`    | PENDING, SUCCESS, FAILED, REFUNDED |
| metadata        | JSONB            | NULL                           | Raw IPN / validation payload       |
| created_at      | TIMESTAMPTZ      | NOT NULL                       | Initiated                          |
| completed_at    | TIMESTAMPTZ      | NULL                           | Gateway success time               |


### 6.3 `content_unlocks`


| Column                 | Type        | Constraints                                    | Description    |
| ---------------------- | ----------- | ---------------------------------------------- | -------------- |
| **id**                 | UUID        | PK                                             | Primary key    |
| user_id                | UUID        | FK → users.id, NOT NULL                        | Buyer / reader |
| post_id                | UUID        | FK → posts.id, NOT NULL                        | Unlocked post  |
| payment_transaction_id | UUID        | FK → payment_transactions.id, UNIQUE, NOT NULL | Source payment |
| unlocked_at            | TIMESTAMPTZ | NOT NULL                                       | Access granted |


*UNIQUE (user_id, post_id) — one purchase per user per post. Drives full read + like/comment eligibility.*

---

## 7. Schema: `reading`

### 7.1 `reading_progress`


| Column           | Type        | Constraints             | Description                 |
| ---------------- | ----------- | ----------------------- | --------------------------- |
| **id**           | UUID        | PK                      | Primary key                 |
| user_id          | UUID        | FK → users.id, NOT NULL | Reader                      |
| post_id          | UUID        | FK → posts.id, NOT NULL | Post being read             |
| scroll_position  | INT         | NOT NULL, DEFAULT 0     | Pixel offset or block index |
| progress_percent | SMALLINT    | NOT NULL, DEFAULT 0     | 0–100 completion            |
| last_read_at     | TIMESTAMPTZ | NOT NULL                | Resume timestamp            |


*UNIQUE (user_id, post_id).*

---

## 8. PostgreSQL ENUM Types


| Enum                             | Values                                    | Used in                     |
| -------------------------------- | ----------------------------------------- | --------------------------- |
| `identity.user_status`           | PENDING_VERIFICATION, VERIFIED, SUSPENDED | users.status                |
| `identity.user_role`             | READER, AUTHOR, ADMIN                     | users.role                  |
| `identity.author_request_status` | PENDING, APPROVED, REJECTED               | author_requests.status      |
| `catalog.pricing_type`           | FREE, LOCKED                              | posts.pricing_type          |
| `catalog.post_status`            | DRAFT, PUBLISHED, UNDER_REVIEW            | posts.status                |
| `billing.payment_status`         | PENDING, SUCCESS, FAILED, REFUNDED        | payment_transactions.status |


---

## 9. Access Control Logic (Application Layer)


| Action                     | Guest             | Logged-in (no purchase)    | Logged-in (purchased / FREE) |
| -------------------------- | ----------------- | -------------------------- | ---------------------------- |
| Browse published posts     | Yes               | Yes                        | Yes                          |
| Read FREE post (full body) | Yes               | Yes                        | Yes                          |
| Read LOCKED post           | preview_body only | preview_body only          | Full body                    |
| Like / comment             | No                | FREE only                  | Yes                          |
| Buy locked post            | No (login first)  | Yes → payment_transactions | N/A (already owned)          |
| Create post                | No                | No (unless AUTHOR)         | Yes (AUTHOR)                 |


---

## 10. Key Indexes

- `identity.users(phone)`, `identity.users(status)`
- `identity.author_requests(user_id, status)` — partial unique WHERE status = `PENDING`
- `catalog.posts(author_id, status, published_at DESC)` — author dashboard & feed
- `catalog.posts(status, published_at DESC) WHERE deleted_at IS NULL` — public catalog
- `catalog.posts USING GIN (body_plain_text gin_trgm_ops)` — search (pg_trgm)
- `billing.content_unlocks(user_id, post_id)` — access check on every read
- `social.post_likes(post_id)`, `social.post_comments(post_id, created_at)`

---

## 11. MVP vs Full SRS (Deferred)

For a lean first release, these SRS entities can be added later without breaking the core model:

- **Books & chapters** — extend catalog with `books`, `chapters` (same pricing/unlock pattern as posts)
- **Double-entry ledger** — replace simple wallet with `billing.ledger` lines
- **Author follows & library shelf** — `social.author_follows`, `reading.library_entries`
- **B2B publishers, IP disputes, reviewer invitations** — separate schemas when needed

---

*Prokash Digital MVP · Database Design · PostgreSQL 16+ · Flyway migrations recommended*