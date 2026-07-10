CREATE TABLE catalog.writing_media (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id       BIGINT NOT NULL,             -- soft ref -> identity.author_profiles
    storage_key     VARCHAR(512) NOT NULL,
    mime_type       VARCHAR(80) NOT NULL,
    file_size_bytes INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_writing_media_author ON catalog.writing_media(author_id);

CREATE TRIGGER trg_writing_media_updated
    BEFORE UPDATE ON catalog.writing_media
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
