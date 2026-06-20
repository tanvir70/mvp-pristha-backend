CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.wallets (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id   BIGINT NOT NULL,                 -- soft ref → identity.users (USER wallets)
    type       VARCHAR(30) NOT NULL DEFAULT 'USER'
               CHECK (type IN ('USER','SYSTEM_COMMISSION','CLEARING')),
    balance    NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_id, type)
);
CREATE TRIGGER trg_wallets_updated
    BEFORE UPDATE ON billing.wallets
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- Seed system wallets (platform commission + external clearing). owner_id 0 = system.
INSERT INTO billing.wallets (owner_id, type) VALUES
    (0, 'SYSTEM_COMMISSION'),
    (0, 'CLEARING');

-- Pending wallet top-ups (mock SSLCommerz lifecycle).
CREATE TABLE billing.top_up_requests (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL,                -- soft ref → identity.users
    amount      NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','SUCCESS','FAILED')),
    gateway_ref VARCHAR(255),                   -- mock txn id
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_topup_user ON billing.top_up_requests(user_id);
CREATE TRIGGER trg_topup_updated
    BEFORE UPDATE ON billing.top_up_requests
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- One row per financial transaction. idempotency_key is the authoritative guard.
CREATE TABLE billing.journal_entries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type            VARCHAR(30) NOT NULL
                    CHECK (type IN ('TOPUP','UNLOCK','WITHDRAWAL')),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only balanced legs. sum(DEBIT) = sum(CREDIT) per journal (app-enforced).
CREATE TABLE billing.ledger_lines (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journal_id BIGINT NOT NULL REFERENCES billing.journal_entries(id),  -- intra-schema FK
    wallet_id  BIGINT NOT NULL REFERENCES billing.wallets(id),          -- intra-schema FK
    direction  VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
    amount     NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_journal ON billing.ledger_lines(journal_id);
CREATE INDEX idx_ledger_wallet  ON billing.ledger_lines(wallet_id);

CREATE TABLE billing.payout_requests (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id         BIGINT NOT NULL,          -- soft ref → identity.author_profiles
    amount            NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    status            VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','PROCESSED','REJECTED')),
    payout_mfs_number VARCHAR(20) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payout_author ON billing.payout_requests(author_id);
CREATE TRIGGER trg_payout_updated
    BEFORE UPDATE ON billing.payout_requests
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
