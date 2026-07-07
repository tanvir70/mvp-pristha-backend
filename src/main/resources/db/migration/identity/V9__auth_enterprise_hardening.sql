-- Social login (Google) support: phone/password become optional, add provider link
ALTER TABLE identity.users ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE identity.users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE identity.users ADD COLUMN google_sub VARCHAR(255);
ALTER TABLE identity.users ADD CONSTRAINT uq_users_google_sub UNIQUE (google_sub);

-- MFA opt-in flag
ALTER TABLE identity.users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Session & device tracking (one row per issued refresh token)
CREATE TABLE identity.user_sessions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES identity.users(id),
    refresh_token_id VARCHAR(64) NOT NULL,
    device_label     VARCHAR(255),
    ip_address       VARCHAR(64),
    user_agent       VARCHAR(512),
    last_used_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked          BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_user_sessions_user_token ON identity.user_sessions(user_id, refresh_token_id);
CREATE INDEX idx_user_sessions_user_active ON identity.user_sessions(user_id) WHERE revoked = FALSE;

CREATE TRIGGER trg_user_sessions_updated
    BEFORE UPDATE ON identity.user_sessions
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- Security audit trail
CREATE TABLE identity.security_audit_logs (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT REFERENCES identity.users(id),
    phone       VARCHAR(20),
    event_type  VARCHAR(40) NOT NULL,
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(512),
    detail      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_security_audit_logs_user ON identity.security_audit_logs(user_id, created_at DESC);

CREATE TRIGGER trg_security_audit_logs_updated
    BEFORE UPDATE ON identity.security_audit_logs
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
