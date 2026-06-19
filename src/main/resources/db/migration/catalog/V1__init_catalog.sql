CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE catalog.posts (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL,
    title VARCHAR(255),
    slug VARCHAR(280) NOT NULL UNIQUE,
    excerpt VARCHAR(500),
    body JSONB NOT NULL,
    body_plain_text TEXT,
    preview_body JSONB,
    cover_image_url VARCHAR(512),
    pricing_type VARCHAR(20) NOT NULL DEFAULT 'FREE',
    price_amount DECIMAL(10, 2),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP WITH TIME ZONE,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_posts_locked_price CHECK (
        (pricing_type = 'LOCKED' AND price_amount >= 1)
        OR (pricing_type = 'FREE' AND price_amount IS NULL)
    )
);

CREATE TABLE catalog.tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(60) NOT NULL UNIQUE,
    slug VARCHAR(70) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE catalog.post_tags (
    post_id BIGINT NOT NULL REFERENCES catalog.posts(id),
    tag_id BIGINT NOT NULL REFERENCES catalog.tags(id),
    PRIMARY KEY (post_id, tag_id)
);

CREATE TABLE catalog.post_media (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT REFERENCES catalog.posts(id),
    author_id BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    mime_type VARCHAR(80) NOT NULL,
    file_size_bytes INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_posts_author_status_published ON catalog.posts(author_id, status, published_at DESC);
CREATE INDEX idx_posts_public_catalog ON catalog.posts(status, published_at DESC) WHERE deleted_at IS NULL;
