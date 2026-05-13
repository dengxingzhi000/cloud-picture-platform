-- =====================================================
-- V13: RBAC Management — Add system flags and audit log
-- Adds is_system columns to role and permission tables
-- Creates rbac_audit_log table for tracking RBAC changes
-- =====================================================

-- ── 1. Add is_system flag to role table ──────────────

ALTER TABLE role ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT false;

UPDATE role SET is_system = true WHERE name IN ('ROLE_USER', 'ROLE_ADMIN');

-- ── 2. Add is_system flag to permission table ────────

ALTER TABLE permission ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT false;

UPDATE permission SET is_system = true WHERE name IN ('picture:create', 'picture:read', 'admin:review');

-- ── 3. Create rbac_audit_log table ───────────────────

CREATE TABLE rbac_audit_log (
    id          UUID PRIMARY KEY,
    entity_type VARCHAR(50)  NOT NULL, -- ROLE, PERMISSION, USER_ROLE
    entity_id   UUID         NOT NULL,
    action      VARCHAR(50)  NOT NULL, -- CREATE, UPDATE, DELETE
    actor_id    UUID         NOT NULL,
    old_value   JSONB,
    new_value   JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_rbac_audit_entity ON rbac_audit_log (entity_type, entity_id);
CREATE INDEX idx_rbac_audit_actor  ON rbac_audit_log (actor_id);
CREATE INDEX idx_rbac_audit_created ON rbac_audit_log (created_at);

-- ── 4. Add RBAC management permissions ───────────────

INSERT INTO permission (id, name, description, resource, action, created_at, updated_at, is_system) VALUES
('b0000000-0000-0000-0000-000000000041', 'admin:role',        'Manage roles',                'admin', 'role',        now(), now(), true),
('b0000000-0000-0000-0000-000000000042', 'admin:permission',  'Manage permissions',           'admin', 'permission',  now(), now(), true),
('b0000000-0000-0000-0000-000000000043', 'admin:user-role',   'Manage user role assignments', 'admin', 'user-role',   now(), now(), true);

-- ── 5. Grant new permissions to ADMIN role ───────────

INSERT INTO role_permission (role_id, permission_id) VALUES
('a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000041'), -- admin:role
('a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000042'), -- admin:permission
('a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000043'); -- admin:user-role
