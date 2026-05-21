# RBAC Management Frontend Spec & Plan

**Date:** 2026-05-21
**Scope:** Complete RBAC management frontend — permissions, roles, role-permission assignment, user-role assignment

## Backend API Inventory

### Permissions (`/api/admin/permissions`)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | `/api/admin/permissions` | `page`, `size`, `resource` (optional) | `PageResponse<PermissionResponse>` |
| GET | `/api/admin/permissions/{id}` | — | `PermissionResponse` |
| POST | `/api/admin/permissions` | `PermissionCreateRequest` | `PermissionResponse` |
| PUT | `/api/admin/permissions/{id}` | `PermissionUpdateRequest` | `PermissionResponse` |
| DELETE | `/api/admin/permissions/{id}` | — | `Void` |

### Roles (`/api/admin/roles`)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | `/api/admin/roles` | `page`, `size` | `PageResponse<RoleResponse>` |
| GET | `/api/admin/roles/{id}` | — | `RoleResponse` |
| POST | `/api/admin/roles` | `RoleCreateRequest` | `RoleResponse` |
| PUT | `/api/admin/roles/{id}` | `RoleUpdateRequest` | `RoleResponse` |
| DELETE | `/api/admin/roles/{id}` | — | `Void` |
| GET | `/api/admin/roles/{id}/permissions` | — | `Set<string>` |
| PUT | `/api/admin/roles/{id}/permissions` | `RolePermissionUpdateRequest` | `Void` |

### User Roles (`/api/admin/users/{userId}/roles`)
| Method | Path | Request | Response |
|--------|------|---------|----------|
| GET | `/api/admin/users/{userId}/roles` | — | `List<UserRoleResponse>` |
| POST | `/api/admin/users/{userId}/roles` | `UserRoleAssignRequest` | `UserRoleResponse` |
| DELETE | `/api/admin/users/{userId}/roles/{roleId}` | — | `Void` |
| PUT | `/api/admin/users/{userId}/roles` | `UserRoleBulkUpdateRequest` | `Void` |

## Frontend Files to Create

### API Layer (`src/api/`)
| File | Functions |
|------|-----------|
| `permissions.ts` | `listPermissions`, `getPermission`, `createPermission`, `updatePermission`, `deletePermission` |
| `roles.ts` | `listRoles`, `getRole`, `createRole`, `updateRole`, `deleteRole`, `getRolePermissions`, `updateRolePermissions` |
| `userRoles.ts` | `getUserRoles`, `assignUserRole`, `removeUserRole`, `updateUserRoles` |

### Pages (`src/react-app/pages/admin/`)
| File | Route | Description |
|------|-------|-------------|
| `AdminPermissionsPage.tsx` | `/admin/permissions` | Permissions list + CRUD dialogs |
| `AdminRolesPage.tsx` | `/admin/roles` | Roles list + CRUD + permission assignment |

### Files to Modify
| File | Change |
|------|--------|
| `AdminLayout.tsx` | Add sidebar nav: Permissions, Roles |
| `App.tsx` | Add routes for new pages |

## Design

### Permissions Page
- Table: name, resource, action, isSystem badge, actions (edit/delete)
- Filter by resource dropdown
- Create dialog: name, description, resource, action
- Edit dialog: same fields
- Delete confirmation (system permissions cannot be deleted)

### Roles Page
- Table: name, description, isSystem badge, permission count, actions
- Create dialog: name, description
- Edit dialog: name, description
- Delete confirmation (system roles cannot be deleted)
- Expandable row or dialog showing permission checkboxes with save

### User Role Assignment
- Integrated into existing user management (if exists) or standalone page
- User search/select → show assigned roles → add/remove roles
- **NOTE:** Backend has no `GET /api/admin/users` endpoint for listing all users. We'll need to either:
  - (a) Add a user list endpoint to backend, or
  - (b) Build a user-search-by-username approach using existing auth endpoints
  
  **Decision:** Skip user-role assignment page for now (no user list endpoint). Add it as a follow-up after backend user list endpoint is created.

## Revised Scope

Given the missing user list endpoint, the RBAC frontend will cover:
1. **Permissions CRUD** — full page
2. **Roles CRUD + permission assignment** — full page
3. **API clients** — for permissions, roles, and user-roles (ready for future use)
4. **Admin layout updates** — sidebar nav + routes
