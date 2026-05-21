-- V27__menu.sql
-- Create menu and role_menu tables for dynamic menu configuration

-- Menu table (tree structure)
CREATE TABLE menu (
    id          UUID PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    code        VARCHAR(50) NOT NULL,
    parent_id   UUID,
    path        VARCHAR(100),
    icon        VARCHAR(50),
    sort_order  INT NOT NULL DEFAULT 0,
    is_visible  BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_menu_code UNIQUE (code),
    CONSTRAINT fk_menu_parent FOREIGN KEY (parent_id) REFERENCES menu(id) ON DELETE SET NULL
);

CREATE INDEX idx_menu_parent ON menu (parent_id);
CREATE INDEX idx_menu_sort ON menu (sort_order);

-- Role-Menu junction table
CREATE TABLE role_menu (
    role_id    UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    menu_id    UUID NOT NULL REFERENCES menu(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, menu_id)
);

CREATE INDEX idx_role_menu_role ON role_menu (role_id);
CREATE INDEX idx_role_menu_menu ON role_menu (menu_id);

-- Seed menus
INSERT INTO menu (id, name, code, parent_id, path, icon, sort_order, is_visible) VALUES
-- Admin root menus
('c0000000-0000-0000-0000-000000000001', 'Dashboard',        'dashboard',         NULL, '/admin',              'layout-dashboard', 1,  true),
('c0000000-0000-0000-0000-000000000002', 'Content Review',   'content_review',    NULL, '/admin/reviews',      'shield-check',     2,  true),
('c0000000-0000-0000-0000-000000000003', 'User Management',  'user_management',   NULL, '/admin/users',        'users',            3,  true),
('c0000000-0000-0000-0000-000000000004', 'Role Management',  'role_management',   NULL, '/admin/roles',        'user-cog',         4,  true),
('c0000000-0000-0000-0000-000000000005', 'Permissions',      'permission_mgmt',   NULL, '/admin/permissions',  'key',              5,  true),
('c0000000-0000-0000-0000-000000000006', 'Search Index',     'search_index',      NULL, '/admin/search-index', 'search',           6,  true);

-- Seed role-menu mappings
-- ROLE_MODERATOR: Dashboard + Content Review
INSERT INTO role_menu (role_id, menu_id) VALUES
('a0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001'), -- Dashboard
('a0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000002'); -- Content Review

-- ROLE_ADMIN: All menus
INSERT INTO role_menu (role_id, menu_id) VALUES
('a0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001'), -- Dashboard
('a0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000002'), -- Content Review
('a0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000003'), -- User Management
('a0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000004'), -- Role Management
('a0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000005'), -- Permissions
('a0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000006'); -- Search Index
