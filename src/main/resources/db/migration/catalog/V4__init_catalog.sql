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
