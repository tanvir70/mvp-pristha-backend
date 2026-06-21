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
