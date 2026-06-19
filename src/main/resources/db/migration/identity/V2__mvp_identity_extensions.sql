ALTER TABLE identity.users
    ALTER COLUMN phone TYPE VARCHAR(15),
    ALTER COLUMN full_name TYPE VARCHAR(120);

ALTER TABLE identity.users
    ADD COLUMN email VARCHAR(255) UNIQUE,
    ADD COLUMN avatar_url VARCHAR(512),
    ADD COLUMN bio TEXT,
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'READER';

UPDATE identity.users SET status = 'VERIFIED' WHERE status = 'ACTIVE';

ALTER TABLE identity.author_profiles
    ALTER COLUMN pen_name DROP NOT NULL;

ALTER TABLE identity.author_profiles
    ADD COLUMN payout_phone VARCHAR(15),
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

UPDATE identity.author_profiles
SET payout_phone = payout_mfs_number
WHERE payout_mfs_number IS NOT NULL;

ALTER TABLE identity.author_profiles
    DROP COLUMN payout_mfs_number,
    DROP COLUMN payout_mfs_provider;

CREATE TABLE identity.author_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES identity.users(id),
    requested_pen_name VARCHAR(80),
    motivation TEXT,
    sample_writing_url VARCHAR(512),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT REFERENCES identity.users(id),
    review_note TEXT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_author_requests_user_status ON identity.author_requests(user_id, status);

CREATE UNIQUE INDEX idx_author_requests_one_pending_per_user
    ON identity.author_requests(user_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_users_status ON identity.users(status);
