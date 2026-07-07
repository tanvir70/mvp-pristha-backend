CREATE SCHEMA IF NOT EXISTS tenant;

CREATE TABLE tenant.tenants (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_tenants_updated
    BEFORE UPDATE ON tenant.tenants
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- MVP default tenant (OVERRIDING so the identity column accepts id = 1).
INSERT INTO tenant.tenants (id, name)
OVERRIDING SYSTEM VALUE VALUES (1, 'Pristha');
