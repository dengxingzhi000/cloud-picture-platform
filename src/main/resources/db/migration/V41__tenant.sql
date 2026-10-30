CREATE TABLE tenant (
    id uuid NOT NULL PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    name varchar(120) NOT NULL,
    slug varchar(80) NOT NULL UNIQUE,
    plan varchar(30) NOT NULL,
    status varchar(20) NOT NULL
);

ALTER TABLE app_user ADD COLUMN tenant_id uuid;
CREATE INDEX idx_user_tenant ON app_user (tenant_id);

-- Seed a default tenant and attach existing users to it (idempotent)
INSERT INTO tenant (id, created_at, updated_at, name, slug, plan, status)
SELECT '00000000-0000-0000-0000-000000000001'::uuid,
       now(),
       now(),
       'Default Tenant',
       'default',
       'FREE',
       'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE slug = 'default');

UPDATE app_user SET tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
WHERE tenant_id IS NULL;
