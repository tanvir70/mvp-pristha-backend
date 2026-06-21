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
