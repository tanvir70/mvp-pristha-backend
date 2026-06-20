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
