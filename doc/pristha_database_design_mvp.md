# Pristha Digital — MVP Database Design (PostgreSQL 17)

> Canonical physical data model for the MVP core loop:
> **writer publishes locked content → reader loads wallet → reader unlocks → reader reads → writer earns.**
>
> Designed fresh from `pristha_srs_mvp_v2.md`. This is the source of truth for
> Flyway migrations. Entities/code should follow this, not the reverse.

---

## 1. Design Principles

1. **One schema per module** (`identity`, `tenant`, `studio`, `catalog`,
   `social`, `reading`, `billing`, `analytics`) so modules can later be
   extracted into separate services / databases.
2. **No cross-schema foreign keys.** A column pointing into another module's
   table is a plain `BIGINT` *soft reference* (documented in a comment).
   Referential integrity across modules is enforced in application code and via
   events — never by the database. FKs are used **only within a single schema**.
3. **Identity columns**, not `SERIAL`: `BIGINT GENERATED ALWAYS AS IDENTITY`
   (Postgres 17 best practice).
4. **Timestamps** are `TIMESTAMPTZ`, default `now()`. `updated_at` is maintained
   by a shared trigger (`shared.set_updated_at`).
5. **Money** is `NUMERIC(15,2)` (catalog list prices `NUMERIC(10,2)`). Never
   floating point. Ledger amounts are stored **positive**; sign comes from
   `direction`.
6. **Tenant scoping:** every tenant-owned row carries `tenant_id`. MVP ships a
   single seeded tenant (`id = 1`).
7. **Soft deletes** via `deleted_at` where content must be recoverable
   (`studio.writings`).
8. **Volatile state is NOT in Postgres.** OTPs, refresh tokens, login lockouts,
   idempotency fast-path, and view-dedup hashes live in **Redis** (§9).

---

## 2. Schema / Table Map

```
identity     users · author_profiles
tenant       tenants
studio       categories · writings · writing_categories
catalog      published_writings · published_writing_tags · follows
social       writing_likes · writing_comments
reading      content_access · library_entries
billing      wallets · top_up_requests · journal_entries · ledger_lines · payout_requests
analytics    content_views · content_unlocks
shared       set_updated_at()  (trigger fn)   ·   event_publication (Spring Modulith)
```

### Cross-module reference map (soft refs only)

```
studio.writings.author_id        ┄┄▶ identity.author_profiles.id
studio.writings.tenant_id        ┄┄▶ tenant.tenants.id
catalog.published_writings.id    ┄┄▶ studio.writings.id        (shared PK)
catalog.published_writings.author_id ┄┄▶ identity.author_profiles.id
catalog.follows.follower_id      ┄┄▶ identity.users.id
catalog.follows.author_id        ┄┄▶ identity.author_profiles.id
social.writing_likes.writing_id  ┄┄▶ studio.writings.id  (= catalog.published_writings.id)
social.writing_likes.user_id     ┄┄▶ identity.users.id
social.writing_comments.writing_id┄┄▶ studio.writings.id
social.writing_comments.user_id  ┄┄▶ identity.users.id
reading.content_access.reader_id ┄┄▶ identity.users.id
reading.content_access.writing_id┄┄▶ studio.writings.id
reading.library_entries.reader_id┄┄▶ identity.users.id
billing.wallets.owner_id         ┄┄▶ identity.users.id   (USER wallets)
billing.payout_requests.author_id┄┄▶ identity.author_profiles.id
analytics.*.writing_id           ┄┄▶ studio.writings.id
analytics.content_unlocks.reader_id ┄┄▶ identity.users.id
```
`┄┄▶` = soft reference (no DB FK).

---

## 3. Shared Helpers

```sql
CREATE SCHEMA IF NOT EXISTS shared;

-- Auto-maintain updated_at on UPDATE.
CREATE OR REPLACE FUNCTION shared.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

> Each table with an `updated_at` column attaches:
> `CREATE TRIGGER trg_set_updated_at BEFORE UPDATE ON <table>
>  FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();`
> (shown once below; replicate per table).

---

## 4. Schema: `identity`

```sql
CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phone         VARCHAR(20)  NOT NULL UNIQUE,
    full_name     VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
                  CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','SUSPENDED')),
    -- reader profile fields
    email         VARCHAR(255),
    avatar_url    VARCHAR(512),
    bio           TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_phone ON identity.users(phone);
CREATE UNIQUE INDEX uq_users_email ON identity.users(email) WHERE email IS NOT NULL;

CREATE TRIGGER trg_users_updated
    BEFORE UPDATE ON identity.users
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE identity.author_profiles (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL UNIQUE
                        REFERENCES identity.users(id),          -- intra-schema FK
    pen_name            VARCHAR(100) NOT NULL UNIQUE,
    biography           TEXT,
    payout_mfs_number   VARCHAR(20) NOT NULL,
    payout_mfs_provider VARCHAR(20) NOT NULL
                        CHECK (payout_mfs_provider IN ('BKASH','NAGAD','ROCKET')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_author_profiles_updated
    BEFORE UPDATE ON identity.author_profiles
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
```

> **Roles are derived**, not stored: any `ACTIVE` user is a `READER`; a user with
> an `author_profiles` row is also an `AUTHOR`.

### Schema Dictionary & Rationale: `identity`

#### Table: `identity.users`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique identifier for each user profile. |
| `phone` | `VARCHAR(20)` | `NOT NULL UNIQUE` | Primary login identifier and destination for sending OTPs. |
| `full_name` | `VARCHAR(100)` | `NOT NULL` | Display name of the user across the platform. |
| `password_hash` | `VARCHAR(255)` | `NOT NULL` | Bcrypt/Argon2 password hash for login verification. |
| `status` | `VARCHAR(30)` | `NOT NULL DEFAULT 'PENDING_VERIFICATION' CHECK (...)` | Lifecycle state: `PENDING_VERIFICATION`, `ACTIVE`, or `SUSPENDED`. |
| `email` | `VARCHAR(255)` | `NULL UNIQUE` (where not null) | Optional contact email for notifications and recovery. |
| `avatar_url` | `VARCHAR(512)` | `NULL` | Link to the user's avatar image. |
| `bio` | `TEXT` | `NULL` | Optional short biography describing the reader/author. |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Registration timestamp. |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Timestamp of the last profile modification. |

#### Table: `identity.author_profiles`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique identifier for the author profile. |
| `user_id` | `BIGINT` | `NOT NULL UNIQUE REFERENCES identity.users(id)` | Link to the core user account; enforces 1:1 user-to-author mapping. |
| `pen_name` | `VARCHAR(100)` | `NOT NULL UNIQUE` | Public pen name displayed on publications. |
| `biography` | `TEXT` | `NULL` | Detailed author bio shown on public author page. |
| `payout_mfs_number` | `VARCHAR(20)` | `NOT NULL` | Mobile Financial Service (MFS) number for payouts. |
| `payout_mfs_provider` | `VARCHAR(20)` | `NOT NULL CHECK (BKASH/NAGAD/ROCKET)` | Provider chosen for payout (`BKASH`, `NAGAD`, `ROCKET`). |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Timestamp of author profile creation. |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Timestamp of the last author profile modification. |

---

## 5. Schema: `tenant`

```sql
CREATE SCHEMA IF NOT EXISTS tenant;

CREATE TABLE tenant.tenants (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_tenants_updated
    BEFORE UPDATE ON tenant.tenants
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- MVP default tenant (OVERRIDING so the identity column accepts id = 1).
INSERT INTO tenant.tenants (id, name)
OVERRIDING SYSTEM VALUE VALUES (1, 'Pristha');
```

> White-label domains/themes are deferred; not created in the MVP.

### Schema Dictionary & Rationale: `tenant`

#### Table: `tenant.tenants`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique identifier for the tenant. |
| `name` | `VARCHAR(100)` | `NOT NULL` | Display name of the tenant (e.g. 'Pristha'). |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Timestamp when tenant was provisioned. |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Timestamp of the last tenant profile update. |

---

## 6. Schema: `studio`

```sql
CREATE SCHEMA IF NOT EXISTS studio;

CREATE TABLE studio.categories (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE studio.writings (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT NOT NULL,               -- soft ref → tenant.tenants
    author_id    BIGINT NOT NULL,               -- soft ref → identity.author_profiles
    parent_id    BIGINT REFERENCES studio.writings(id),  -- intra-schema self FK (chapter→book)
    title        VARCHAR(255),
    slug         VARCHAR(280),                   -- URL-friendly identifier, set on publish
    body_json    JSONB,                          -- TipTap/EditorJS payload
    preview_json JSONB,                          -- explicit teaser for LOCKED content;
                                                  -- NULL ⇒ reading module auto-truncates body_json
    type         VARCHAR(30) NOT NULL
                 CHECK (type IN ('BOOK','CHAPTER','POST')),
    status       VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
                 CHECK (status IN ('DRAFT','UNFINISHED_PREVIEW','PUBLISHED','COMPLETED')),
    price_type   VARCHAR(20) NOT NULL DEFAULT 'FREE'
                 CHECK (price_type IN ('FREE','LOCKED')),
    price_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00
                 CHECK (price_amount >= 0),
    order_index  INT NOT NULL DEFAULT 0,
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- a LOCKED writing must cost at least 1 BDT
    CONSTRAINT chk_locked_price CHECK (price_type = 'FREE' OR price_amount >= 1),
    -- only chapters have a parent
    CONSTRAINT chk_parent CHECK (parent_id IS NULL OR type = 'CHAPTER')
);
CREATE UNIQUE INDEX uq_writings_slug ON studio.writings(slug) WHERE slug IS NOT NULL;
CREATE INDEX idx_writings_author  ON studio.writings(author_id);
CREATE INDEX idx_writings_parent  ON studio.writings(parent_id);
CREATE INDEX idx_writings_tenant  ON studio.writings(tenant_id);
CREATE INDEX idx_writings_status  ON studio.writings(status);
CREATE INDEX idx_writings_live    ON studio.writings(author_id)
       WHERE deleted_at IS NULL;

CREATE TRIGGER trg_writings_updated
    BEFORE UPDATE ON studio.writings
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE studio.writing_categories (
    writing_id  BIGINT NOT NULL REFERENCES studio.writings(id),
    category_id BIGINT NOT NULL REFERENCES studio.categories(id),
    PRIMARY KEY (writing_id, category_id)
);
CREATE INDEX idx_writing_categories_cat ON studio.writing_categories(category_id);
```

### Schema Dictionary & Rationale: `studio`

#### Table: `studio.categories`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique category identifier. |
| `name` | `VARCHAR(50)` | `NOT NULL UNIQUE` | Display name of the category (e.g. 'Fiction', 'Poetry'). |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Creation timestamp. |

#### Table: `studio.writings`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique writing identifier. |
| `tenant_id` | `BIGINT` | `NOT NULL` | Soft reference to `tenant.tenants(id)` for tenant isolation. |
| `author_id` | `BIGINT` | `NOT NULL` | Soft reference to `identity.author_profiles(id)` for authorship ownership. |
| `parent_id` | `BIGINT` | `REFERENCES studio.writings(id)` | Intra-schema self FK establishing book-to-chapter hierarchy. |
| `title` | `VARCHAR(255)` | `NULL` | Title of the book, chapter, or post. |
| `slug` | `VARCHAR(280)` | `NULL UNIQUE` (where not null) | URL-friendly identifier, assigned on publish; copied into `catalog.published_writings`. |
| `body_json` | `JSONB` | `NULL` | Structured content representation (rich text TipTap/EditorJS payload). |
| `preview_json` | `JSONB` | `NULL` | Explicit author-controlled teaser shown to non-purchasers of `LOCKED` content; if `NULL`, the reading module auto-truncates `body_json`. |
| `type` | `VARCHAR(30)` | `NOT NULL CHECK (BOOK/CHAPTER/POST)` | Content type grouping (`BOOK`, `CHAPTER`, `POST`). |
| `status` | `VARCHAR(30)` | `NOT NULL DEFAULT 'DRAFT'` | Workflow state: `DRAFT`, `UNFINISHED_PREVIEW`, `PUBLISHED`, `COMPLETED`. |
| `price_type` | `VARCHAR(20)` | `NOT NULL DEFAULT 'FREE'` | Paywall configuration: `FREE` or `LOCKED` (behind a paywall). |
| `price_amount` | `NUMERIC(10,2)` | `NOT NULL DEFAULT 0.00` | Price of the content in BDT. Enforced to be $\ge 1.00$ BDT for `LOCKED` content. |
| `order_index` | `INT` | `NOT NULL DEFAULT 0` | Ordering index for sorting chapters within a book. |
| `deleted_at` | `TIMESTAMPTZ` | `NULL` | Timestamp for soft deletion and eventual content recovery. |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Creation timestamp. |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Last update timestamp. |

#### Table: `studio.writing_categories`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `writing_id` | `BIGINT` | `NOT NULL REFERENCES studio.writings(id)` | Part of composite PK. Links category mapping back to writing. |
| `category_id` | `BIGINT` | `NOT NULL REFERENCES studio.categories(id)` | Part of composite PK. Links category mapping to a specific category. |

---

## 7. Schema: `catalog`

> Read-optimized projection of published content, maintained by the
> `ContentPublishedEvent` listener. PK is **shared** with `studio.writings.id`.

```sql
CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE catalog.published_writings (
    id              BIGINT PRIMARY KEY,         -- = studio.writings.id (assigned, no FK)
    tenant_id       BIGINT NOT NULL,
    author_id       BIGINT NOT NULL,            -- soft ref
    author_pen_name VARCHAR(100) NOT NULL,      -- denormalized at index time
    parent_id       BIGINT,                     -- book id for chapters (soft ref)
    title           VARCHAR(255),
    slug            VARCHAR(280) NOT NULL UNIQUE, -- copied from studio.writings at publish time
    synopsis        TEXT,
    cover_image_url VARCHAR(512),
    preview_json    JSONB,                       -- copied from studio.writings; teaser for LOCKED items
    type            VARCHAR(30) NOT NULL,
    status          VARCHAR(30) NOT NULL,       -- mirrors studio status
    price_type      VARCHAR(20) NOT NULL,
    price_amount    NUMERIC(10,2) NOT NULL,
    order_index     INT NOT NULL DEFAULT 0,
    like_count      BIGINT NOT NULL DEFAULT 0,   -- maintained by social module's events
    comment_count   BIGINT NOT NULL DEFAULT 0,   -- maintained by social module's events
    published_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Full-text search vector ('simple' config tokenizes Bangla + English
    -- without English stemming). Generated, always in sync.
    search_tsv TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('simple',
            coalesce(title,'') || ' ' ||
            coalesce(synopsis,'') || ' ' ||
            coalesce(author_pen_name,''))
    ) STORED
);
CREATE INDEX idx_pub_search   ON catalog.published_writings USING GIN(search_tsv);
CREATE INDEX idx_pub_author   ON catalog.published_writings(author_id);
CREATE INDEX idx_pub_parent   ON catalog.published_writings(parent_id);
CREATE INDEX idx_pub_feed     ON catalog.published_writings(published_at DESC);

-- Denormalized tags for filter/search (category names copied from studio).
CREATE TABLE catalog.published_writing_tags (
    writing_id BIGINT NOT NULL REFERENCES catalog.published_writings(id),
    tag        VARCHAR(50) NOT NULL,
    PRIMARY KEY (writing_id, tag)
);
CREATE INDEX idx_pub_tags_tag ON catalog.published_writing_tags(tag);

CREATE TABLE catalog.follows (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    follower_id BIGINT NOT NULL,                -- soft ref → identity.users
    author_id   BIGINT NOT NULL,                -- soft ref → identity.author_profiles
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (follower_id, author_id)
);
CREATE INDEX idx_follows_author ON catalog.follows(author_id);
```

**Feed query** (CAT-FR-01): join `follows` for a reader → `published_writings`
ordered by `published_at DESC`. **Search** (CAT-FR-02):
`WHERE search_tsv @@ websearch_to_tsquery('simple', :q)` optionally joined to
`published_writing_tags`.

### Schema Dictionary & Rationale: `catalog`

#### Table: `catalog.published_writings`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY` | Assigned key mirroring `studio.writings(id)`. Avoids join query overhead. |
| `tenant_id` | `BIGINT` | `NOT NULL` | Soft reference to `tenant.tenants(id)` for multi-tenancy support. |
| `author_id` | `BIGINT` | `NOT NULL` | Soft reference to `identity.author_profiles(id)`. |
| `author_pen_name` | `VARCHAR(100)` | `NOT NULL` | Denormalized pen name to avoid join query overhead when displaying or filtering lists. |
| `parent_id` | `BIGINT` | `NULL` | Soft reference to parent book ID (if a chapter). |
| `title` | `VARCHAR(255)` | `NULL` | Title of the published work. |
| `slug` | `VARCHAR(280)` | `NOT NULL UNIQUE` | URL-friendly identifier copied from `studio.writings.slug` at publish time. |
| `synopsis` | `TEXT` | `NULL` | Short summary of the work for readers browsing catalog/feeds. |
| `cover_image_url` | `VARCHAR(512)` | `NULL` | Link to the book/post cover artwork. |
| `preview_json` | `JSONB` | `NULL` | Teaser content for `LOCKED` writings, copied from `studio.writings.preview_json`; served to readers without access. |
| `type` | `VARCHAR(30)` | `NOT NULL` | Mirror of writing type (`BOOK`/`CHAPTER`/`POST`). |
| `status` | `VARCHAR(30)` | `NOT NULL` | Mirror of writing status. |
| `price_type` | `VARCHAR(20)` | `NOT NULL` | Mirror of writing price type (`FREE`/`LOCKED`). |
| `price_amount` | `NUMERIC(10,2)` | `NOT NULL` | Mirror of writing price. |
| `order_index` | `INT` | `NOT NULL DEFAULT 0` | Sorting order of chapter. |
| `like_count` | `BIGINT` | `NOT NULL DEFAULT 0` | Denormalized like total, updated asynchronously by `social` module events (`WritingLikedEvent`/`WritingUnlikedEvent`). |
| `comment_count` | `BIGINT` | `NOT NULL DEFAULT 0` | Denormalized comment total, updated asynchronously by `social` module events (`CommentPostedEvent`/`CommentDeletedEvent`). |
| `published_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Publication time. Used for ordering catalog lists and home feed query. |
| `search_tsv` | `TSVECTOR` | `GENERATED ALWAYS AS ... STORED` | Full-text search vector combining title, synopsis, and author pen name for fast text matching. |

#### Table: `catalog.published_writing_tags`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `writing_id` | `BIGINT` | `NOT NULL REFERENCES catalog.published_writings(id)` | Part of composite PK. Links tag back to published writing. |
| `tag` | `VARCHAR(50)` | `NOT NULL` | Category name denormalized as a text tag for fast indexing and filtering. |

#### Table: `catalog.follows`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique follow record ID. |
| `follower_id` | `BIGINT` | `NOT NULL` | Soft reference to `identity.users(id)` denoting the reader who is following. |
| `author_id` | `BIGINT` | `NOT NULL` | Soft reference to `identity.author_profiles(id)` denoting the author being followed. |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Follow action timestamp. |

---

## 8. Schema: `reading`

```sql
CREATE SCHEMA IF NOT EXISTS reading;

-- Access grants. Written by billing's unlock txn via ReadingAccessService.
CREATE TABLE reading.content_access (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reader_id  BIGINT NOT NULL,                 -- soft ref → identity.users
    writing_id BIGINT NOT NULL,                 -- soft ref → studio.writings
    source     VARCHAR(20) NOT NULL DEFAULT 'PURCHASE'
               CHECK (source IN ('PURCHASE','GIFT','FREE')),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (reader_id, writing_id)              -- idempotent grant
);
CREATE INDEX idx_access_reader ON reading.content_access(reader_id);

CREATE TABLE reading.library_entries (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reader_id            BIGINT NOT NULL,        -- soft ref → identity.users
    writing_id           BIGINT NOT NULL,        -- the BOOK or POST (soft ref)
    last_read_chapter_id BIGINT,
    last_read_page_num   INT NOT NULL DEFAULT 1,
    last_read_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (reader_id, writing_id)
);
CREATE INDEX idx_library_reader_recent
    ON reading.library_entries(reader_id, last_read_at DESC);
```

### Schema Dictionary & Rationale: `reading`

#### Table: `reading.content_access`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique access grant identifier. |
| `reader_id` | `BIGINT` | `NOT NULL` | Soft reference to `identity.users(id)` representing the reader. |
| `writing_id` | `BIGINT` | `NOT NULL` | Soft reference to `studio.writings(id)` representing the unlocked chapter/post. |
| `source` | `VARCHAR(20)` | `NOT NULL DEFAULT 'PURCHASE'` | Source of the access (`PURCHASE`, `GIFT`, `FREE`). |
| `granted_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Timestamp when the reader unlocked or was granted access to the content. |

#### Table: `reading.library_entries`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique library entry ID. |
| `reader_id` | `BIGINT` | `NOT NULL` | Soft reference to `identity.users(id)` representing the reader. |
| `writing_id` | `BIGINT` | `NOT NULL` | Soft reference to the book or post added to the library. |
| `last_read_chapter_id` | `BIGINT` | `NULL` | Soft reference to the last read chapter ID under the book. |
| `last_read_page_num` | `INT` | `NOT NULL DEFAULT 1` | Last read page/offset within the chapter. |
| `last_read_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Timestamp of last reading activity. |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Timestamp when content was added to the library. |

---

## 9. Schema: `billing`

> True journal + balanced legs. Wallets are mutable balances; ledger lines are
> append-only and must net to zero per journal.

```sql
CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.wallets (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id   BIGINT NOT NULL,                 -- soft ref → identity.users (USER wallets)
    type       VARCHAR(30) NOT NULL DEFAULT 'USER'
               CHECK (type IN ('USER','SYSTEM_COMMISSION','CLEARING')),
    balance    NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_id, type)
);
CREATE TRIGGER trg_wallets_updated
    BEFORE UPDATE ON billing.wallets
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- Seed system wallets (platform commission + external clearing). owner_id 0 = system.
INSERT INTO billing.wallets (owner_id, type) VALUES
    (0, 'SYSTEM_COMMISSION'),
    (0, 'CLEARING');

-- Pending wallet top-ups (mock SSLCommerz lifecycle).
CREATE TABLE billing.top_up_requests (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL,                -- soft ref → identity.users
    amount      NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','SUCCESS','FAILED')),
    gateway_ref VARCHAR(255),                   -- mock txn id
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_topup_user ON billing.top_up_requests(user_id);
CREATE TRIGGER trg_topup_updated
    BEFORE UPDATE ON billing.top_up_requests
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- One row per financial transaction. idempotency_key is the authoritative guard.
CREATE TABLE billing.journal_entries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type            VARCHAR(30) NOT NULL
                    CHECK (type IN ('TOPUP','UNLOCK','WITHDRAWAL')),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only balanced legs. sum(DEBIT) = sum(CREDIT) per journal (app-enforced).
CREATE TABLE billing.ledger_lines (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journal_id BIGINT NOT NULL REFERENCES billing.journal_entries(id),  -- intra-schema FK
    wallet_id  BIGINT NOT NULL REFERENCES billing.wallets(id),          -- intra-schema FK
    direction  VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
    amount     NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_journal ON billing.ledger_lines(journal_id);
CREATE INDEX idx_ledger_wallet  ON billing.ledger_lines(wallet_id);

CREATE TABLE billing.payout_requests (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id         BIGINT NOT NULL,          -- soft ref → identity.author_profiles
    amount            NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    status            VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','PROCESSED','REJECTED')),
    payout_mfs_number VARCHAR(20) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payout_author ON billing.payout_requests(author_id);
CREATE TRIGGER trg_payout_updated
    BEFORE UPDATE ON billing.payout_requests
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
```

**Unlock transaction (BILL-FR-03/04)** — one `journal_entries` row +
3 `ledger_lines`:
| direction | wallet | amount |
|-----------|--------|--------|
| DEBIT  | reader USER wallet | `price` |
| CREDIT | author USER wallet | `price × 0.85` |
| CREDIT | SYSTEM_COMMISSION wallet | `price × 0.15` |

…then balances updated and `reading.content_access` granted, all in one DB txn.

### Schema Dictionary & Rationale: `billing`

#### Table: `billing.wallets`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique wallet identifier. |
| `owner_id` | `BIGINT` | `NOT NULL` | Soft reference to user ID, or `0` for system wallets. |
| `type` | `VARCHAR(30)` | `NOT NULL DEFAULT 'USER'` | Wallet role: `USER` (readers/authors), `SYSTEM_COMMISSION` (platform commission), or `CLEARING` (transit bucket for deposits). |
| `balance` | `NUMERIC(15,2)` | `NOT NULL DEFAULT 0.00` | Current balance. Enforces positive monetary amounts. |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Wallet creation timestamp. |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Last update timestamp (managed by trigger on balance modifications). |

#### Table: `billing.top_up_requests`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique top-up request identifier. |
| `user_id` | `BIGINT` | `NOT NULL` | Soft reference to `identity.users(id)` representing the depositing reader. |
| `amount` | `NUMERIC(15,2)` | `NOT NULL CHECK (amount > 0)` | BDT amount requested to be deposited. |
| `status` | `VARCHAR(20)` | `NOT NULL DEFAULT 'PENDING'` | State of top-up: `PENDING`, `SUCCESS`, or `FAILED`. |
| `gateway_ref` | `VARCHAR(255)` | `NULL` | Transaction ID from the gateway (e.g. SSLCommerz) to correlate requests. |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Deposit request initiation timestamp. |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Timestamp of last state change. |

#### Table: `billing.journal_entries`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique journal entry identifier (representing the transaction header). |
| `type` | `VARCHAR(30)` | `NOT NULL CHECK (TOPUP/UNLOCK/WITHDRAWAL)` | Transaction action type: `TOPUP`, `UNLOCK` (purchase), or `WITHDRAWAL`. |
| `idempotency_key` | `VARCHAR(255)` | `NOT NULL UNIQUE` | Guards against duplicate transaction processing (e.g., double paywall unlocks). |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Transaction finalization timestamp. |

#### Table: `billing.ledger_lines`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique ledger line identifier. |
| `journal_id` | `BIGINT` | `NOT NULL REFERENCES billing.journal_entries(id)` | Intra-schema FK pointing to the transaction header. |
| `wallet_id` | `BIGINT` | `NOT NULL REFERENCES billing.wallets(id)` | Intra-schema FK pointing to the modified wallet. |
| `direction` | `VARCHAR(6)` | `NOT NULL CHECK (DEBIT/CREDIT)` | Account balance movement indicator (`DEBIT` or `CREDIT`). |
| `amount` | `NUMERIC(15,2)` | `NOT NULL CHECK (amount > 0)` | Absolute positive transaction amount in BDT. |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Audit line timestamp. |

#### Table: `billing.payout_requests`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique payout request identifier. |
| `author_id` | `BIGINT` | `NOT NULL` | Soft reference to `identity.author_profiles(id)`. |
| `amount` | `NUMERIC(15,2)` | `NOT NULL CHECK (amount > 0)` | BDT amount requested to be withdrawn by the author. |
| `status` | `VARCHAR(30)` | `NOT NULL DEFAULT 'PENDING'` | Current lifecycle state: `PENDING`, `PROCESSED`, or `REJECTED`. |
| `payout_mfs_number` | `VARCHAR(20)` | `NOT NULL` | Author's MFS account number recorded at request time. |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Withdrawal request timestamp. |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Last state update timestamp. |

---

## 10. Schema: `analytics`

```sql
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.content_views (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    writing_id          BIGINT NOT NULL,         -- soft ref → studio.writings
    reader_session_hash VARCHAR(255) NOT NULL,   -- dedup key (also in Redis 1h)
    viewed_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_views_writing ON analytics.content_views(writing_id);

-- Populated by ContentUnlockedEvent listener (decoupled from billing txn).
CREATE TABLE analytics.content_unlocks (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    writing_id  BIGINT NOT NULL,                 -- soft ref → studio.writings
    reader_id   BIGINT NOT NULL,                 -- soft ref → identity.users
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_unlocks_writing ON analytics.content_unlocks(writing_id);
```

### Schema Dictionary & Rationale: `analytics`

#### Table: `analytics.content_views`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique view log identifier. |
| `writing_id` | `BIGINT` | `NOT NULL` | Soft reference to `studio.writings(id)`. |
| `reader_session_hash` | `VARCHAR(255)` | `NOT NULL` | Session hash used for sliding-window view deduplication (typically 1 hour). |
| `viewed_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | View occurrence timestamp. |

#### Table: `analytics.content_unlocks`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique unlock log identifier. |
| `writing_id` | `BIGINT` | `NOT NULL` | Soft reference to `studio.writings(id)`. |
| `reader_id` | `BIGINT` | `NOT NULL` | Soft reference to `identity.users(id)`. |
| `unlocked_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Timestamp of content unlock, decoupled from billing transaction for async analysis. |

---

## 11. Schema: `social`

> Likes and comments, gated by read access (`FREE` writing OR an existing
> `reading.content_access` row). Eligibility is checked in the `social`
> module's service layer — never via DB constraint — consistent with our
> no-cross-schema-integrity principle (§1.2).

```sql
CREATE SCHEMA IF NOT EXISTS social;

CREATE TABLE social.writing_likes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    writing_id BIGINT NOT NULL,                 -- soft ref → studio.writings / catalog.published_writings
    user_id    BIGINT NOT NULL,                 -- soft ref → identity.users
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (writing_id, user_id)                -- one like per reader per writing
);
CREATE INDEX idx_likes_writing ON social.writing_likes(writing_id);

CREATE TABLE social.writing_comments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    writing_id BIGINT NOT NULL,                 -- soft ref → studio.writings
    user_id    BIGINT NOT NULL,                 -- soft ref → identity.users
    parent_id  BIGINT REFERENCES social.writing_comments(id),  -- intra-schema self FK (reply thread)
    body       TEXT NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_comments_writing ON social.writing_comments(writing_id, created_at);
CREATE TRIGGER trg_comments_updated
    BEFORE UPDATE ON social.writing_comments
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
```

A `WritingLikedEvent`/`WritingUnlikedEvent`/`CommentPostedEvent`/
`CommentDeletedEvent` listener in `catalog` keeps
`published_writings.like_count` / `comment_count` (§7) in sync —
eventually consistent, same pattern as `ContentUnlockedEvent` → `analytics`.

### Schema Dictionary & Rationale: `social`

#### Table: `social.writing_likes`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique like record identifier. |
| `writing_id` | `BIGINT` | `NOT NULL` | Soft reference to `studio.writings(id)` / `catalog.published_writings(id)`. |
| `user_id` | `BIGINT` | `NOT NULL` | Soft reference to `identity.users(id)` — the liking reader. |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Like timestamp. |

*Eligibility (read access) enforced in application code, not by a DB constraint.*

#### Table: `social.writing_comments`
| Column | Data Type | Constraints | Why Needed / Purpose |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `GENERATED ALWAYS AS IDENTITY PRIMARY KEY` | Unique comment identifier. |
| `writing_id` | `BIGINT` | `NOT NULL` | Soft reference to `studio.writings(id)`. |
| `user_id` | `BIGINT` | `NOT NULL` | Soft reference to `identity.users(id)` — the commenting reader. |
| `parent_id` | `BIGINT` | `NULL REFERENCES social.writing_comments(id)` | Intra-schema self FK for threaded replies. |
| `body` | `TEXT` | `NOT NULL` | Plain-text comment body. |
| `deleted_at` | `TIMESTAMPTZ` | `NULL` | Soft delete (preserve thread shape for replies). |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Comment timestamp. |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Last edit timestamp. |

---

## 12. Spring Modulith Event Registry

The DB-backed event publication registry (at-least-once async delivery) creates
its own table automatically (`spring-modulith-starter-jpa`). Keep it in a
neutral schema:

```sql
CREATE SCHEMA IF NOT EXISTS events;
-- event_publication is auto-created by Spring Modulith on startup.
```
Set `spring.modulith.events.jdbc.schema=events` (or let it default to `public`).

---

## 13. State that lives in Redis (NOT Postgres)

| Key pattern | Purpose | TTL |
|-------------|---------|-----|
| `otp:{phone}` | signup / reset OTP code | 5 min |
| `otp_attempts:{phone}` | OTP verify attempt counter (cap 5) | 5 min |
| `otp_resend:{phone}` | resend cooldown | 60 s |
| `login_lock:{phone}` | login lockout after 5 fails | 15 min |
| `refresh:{userId}:{tokenId}` | active refresh tokens | 30 days |
| `idem:{key}` | unlock idempotency fast-path (authority = DB unique) | 24 h |
| `view:{writingId}:{sessionHash}` | view dedup | 1 h |
| `page:{writingId}` / `pub:{writingId}` | cached raw body / projection | tunable |

---

## 14. Flyway Layout

Per-module folders (matches existing project + "sellable in chunks"):

```
src/main/resources/db/migration/
  identity/   V1__init_identity.sql
  tenant/     V2__init_tenant.sql
  studio/     V3__init_studio.sql
  catalog/    V4__init_catalog.sql
  social/     V5__init_social.sql
  reading/    V6__init_reading.sql
  billing/    V7__init_billing.sql
  analytics/  V8__init_analytics.sql
  shared/     V0__shared_functions.sql   (set_updated_at; run first)
```
```properties
spring.flyway.locations=classpath:db/migration/shared,\
  classpath:db/migration/identity,classpath:db/migration/tenant,\
  classpath:db/migration/studio,classpath:db/migration/catalog,\
  classpath:db/migration/social,classpath:db/migration/reading,\
  classpath:db/migration/billing,classpath:db/migration/analytics
```
> Versioned numbers are global and must stay monotonic across folders. Use the
> `V<n>__` ordering above (shared = V0).

---

## 15. ER Overview (per schema; ┄ = soft cross-module ref)

```
identity:  users 1───1 author_profiles

studio:    categories ╲                writings ──self──▶ writings (book↔chapter)
                       ╲── writing_categories ──╱

catalog:   published_writings 1───* published_writing_tags
           follows  (follower ┄ users, author ┄ author_profiles)
           published_writings.id ┄ studio.writings.id

social:    writing_likes     (user ┄ users, writing ┄ writings)
           writing_comments  (user ┄ users, writing ┄ writings) ──self──▶ comments (replies)

reading:   content_access   (reader ┄ users, writing ┄ writings)
           library_entries  (reader ┄ users, writing ┄ writings)

billing:   wallets 1───* ledger_lines *───1 journal_entries
           top_up_requests · payout_requests
           (owner/author ┄ identity)

analytics: content_views · content_unlocks   (┄ studio / identity)
```

---

## 16. Key Decisions Recap

- **Catalog PK shared with studio** (`published_writings.id = writings.id`) so a
  reader/feed entry maps 1:1 to source content without a join across schemas.
- **Tags denormalized into catalog** (`published_writing_tags`) so search/filter
  never reaches into `studio`.
- **System wallets** seeded with `owner_id = 0` (`SYSTEM_COMMISSION`, `CLEARING`)
  so journals always balance, including top-ups (CLEARING ↔ user) and
  commission (reader → author + commission).
- **`balance` is a single column** (no pending/escrow split) — settlement holds
  & disputes are deferred; reconcile `wallets.balance` against
  `Σ ledger_lines` in tests.
- **FTS uses `'simple'`** config: no English stemming, so Bangla and English
  tokens both index predictably. Upgrade to a custom Bangla dictionary or
  Elasticsearch only at enterprise tier.
- **No DB-level cross-schema integrity** anywhere — that is by design, the cost
  of keeping every module independently deployable.
- **Author onboarding is open, not admin-gated**: any `ACTIVE` user may create
  an `identity.author_profiles` row and start writing. No `author_requests`
  approval workflow in MVP (confirmed product decision).
- **Likes & comments are in MVP** (`social` schema), gated by read access
  (`FREE` writing OR an existing `reading.content_access` row), checked in
  app code — not a DB constraint. Counts are denormalized onto
  `catalog.published_writings` via async events, same pattern as analytics.
