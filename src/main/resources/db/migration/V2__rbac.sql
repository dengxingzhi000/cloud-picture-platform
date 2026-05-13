-- =====================================================
-- V2: RBAC — Role-Based Access Control
-- Adds role, permission, user_role, role_permission tables
-- Migrates existing app_user.role data, then drops the column
-- =====================================================

-- ── 1. role ────────────────────────────────────────────

CREATE TABLE role (
    id          UUID PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_role_name UNIQUE (name)
);

CREATE INDEX idx_role_name ON role (name);

-- ── 2. permission ──────────────────────────────────────

CREATE TABLE permission (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(200),
    resource    VARCHAR(50)  NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_permission_name UNIQUE (name),
    CONSTRAINT uk_permission_resource_action UNIQUE (resource, action)
);

CREATE INDEX idx_permission_resource ON permission (resource);
CREATE INDEX idx_permission_action   ON permission (action);

-- ── 3. user_role (M:N junction) ────────────────────────

CREATE TABLE user_role (
    user_id    UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id    UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_role_user ON user_role (user_id);
CREATE INDEX idx_user_role_role ON user_role (role_id);

-- ── 4. role_permission (M:N junction) ──────────────────

CREATE TABLE role_permission (
    role_id       UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permission_role ON role_permission (role_id);
CREATE INDEX idx_role_permission_perm ON role_permission (permission_id);

-- ── 5. Seed roles ──────────────────────────────────────

INSERT INTO role (id, name, description, created_at, updated_at) VALUES
('a0000000-0000-0000-0000-000000000001', 'ROLE_USER',      'Default user role',             now(), now()),
('a0000000-0000-0000-0000-000000000002', 'ROLE_MODERATOR', 'Content moderator (reserved)',   now(), now()),
('a0000000-0000-0000-0000-000000000003', 'ROLE_ADMIN',     'System administrator',           now(), now());

-- ── 6. Seed permissions ────────────────────────────────

INSERT INTO permission (id, name, description, resource, action, created_at, updated_at) VALUES
-- picture
('b0000000-0000-0000-0000-000000000001', 'picture:create', 'Upload pictures',                'picture', 'create', now(), now()),
('b0000000-0000-0000-0000-000000000002', 'picture:read',   'View picture details',           'picture', 'read',   now(), now()),
('b0000000-0000-0000-0000-000000000003', 'picture:update', 'Edit picture metadata',           'picture', 'update', now(), now()),
('b0000000-0000-0000-0000-000000000004', 'picture:delete', 'Delete pictures',                 'picture', 'delete', now(), now()),
('b0000000-0000-0000-0000-000000000005', 'picture:tag',    'Manage tags on pictures',         'picture', 'tag',    now(), now()),
-- tag
('b0000000-0000-0000-0000-000000000011', 'tag:create',     'Create tags in catalog',          'tag',     'create', now(), now()),
('b0000000-0000-0000-0000-000000000012', 'tag:read',       'View tags',                       'tag',     'read',   now(), now()),
('b0000000-0000-0000-0000-000000000013', 'tag:update',     'Update tags',                     'tag',     'update', now(), now()),
('b0000000-0000-0000-0000-000000000014', 'tag:delete',     'Delete tags',                     'tag',     'delete', now(), now()),
-- team
('b0000000-0000-0000-0000-000000000021', 'team:create',        'Create teams',               'team',    'create',        now(), now()),
('b0000000-0000-0000-0000-000000000022', 'team:read',          'View teams and members',     'team',    'read',          now(), now()),
('b0000000-0000-0000-0000-000000000023', 'team:update',        'Update team settings',       'team',    'update',        now(), now()),
('b0000000-0000-0000-0000-000000000024', 'team:delete',        'Delete teams',               'team',    'delete',        now(), now()),
('b0000000-0000-0000-0000-000000000025', 'team:invite',        'Invite members to teams',    'team',    'invite',        now(), now()),
('b0000000-0000-0000-0000-000000000026', 'team:manage-roles',  'Manage member roles',        'team',    'manage-roles',  now(), now()),
('b0000000-0000-0000-0000-000000000027', 'team:remove-member', 'Remove team members',        'team',    'remove-member', now(), now()),
-- admin
('b0000000-0000-0000-0000-000000000031', 'admin:review',       'Moderate picture submissions','admin',   'review',  now(), now()),
('b0000000-0000-0000-0000-000000000032', 'admin:search',       'Manage search index',        'admin',   'search',  now(), now()),
('b0000000-0000-0000-0000-000000000033', 'admin:user',         'Manage users (reserved)',    'admin',   'user',    now(), now());

-- ── 7. Seed role-permission mappings ───────────────────

-- ROLE_USER: basic picture, tag, team operations
INSERT INTO role_permission (role_id, permission_id) VALUES
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001'), -- picture:create
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000002'), -- picture:read
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000003'), -- picture:update
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000004'), -- picture:delete
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000005'), -- picture:tag
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000011'), -- tag:create
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000012'), -- tag:read
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000013'), -- tag:update
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000014'), -- tag:delete
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000021'), -- team:create
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000022'), -- team:read
('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000025'); -- team:invite

-- ROLE_MODERATOR: USER permissions + admin:review
INSERT INTO role_permission (role_id, permission_id)
SELECT 'a0000000-0000-0000-0000-000000000002', permission_id
FROM role_permission
WHERE role_id = 'a0000000-0000-0000-0000-000000000001';
INSERT INTO role_permission (role_id, permission_id) VALUES
('a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000031'); -- admin:review

-- ROLE_ADMIN: all permissions
INSERT INTO role_permission (role_id, permission_id)
SELECT 'a0000000-0000-0000-0000-000000000003', permission_id
FROM permission;

-- ── 8. Migrate existing user roles ─────────────────────

INSERT INTO user_role (user_id, role_id)
SELECT u.id,
       CASE u.role
           WHEN 'ADMIN' THEN 'a0000000-0000-0000-0000-000000000003'::uuid
           ELSE              'a0000000-0000-0000-0000-000000000001'::uuid
       END
FROM app_user u
WHERE NOT EXISTS (
    SELECT 1 FROM user_role ur WHERE ur.user_id = u.id
);

-- ── 9. Drop old role column from app_user ──────────────

ALTER TABLE app_user DROP COLUMN role;
