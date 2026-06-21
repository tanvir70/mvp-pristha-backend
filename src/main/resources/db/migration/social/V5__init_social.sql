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
