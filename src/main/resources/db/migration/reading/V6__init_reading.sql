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
