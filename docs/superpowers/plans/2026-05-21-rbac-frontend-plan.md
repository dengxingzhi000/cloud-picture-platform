# RBAC Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build complete RBAC management frontend — permissions CRUD, roles CRUD with permission assignment, admin layout updates.

**Tech Stack:** React 19, TypeScript, Vite, Tailwind CSS, Radix UI, Lucide icons, Axios

**Frontend working directory:** `D:\ProgramProject\PolymerizationProject\cloud-picture-platform-web`

---

### Task 1: Create permissions API client

**Files:** Create `src/api/permissions.ts`

```typescript
import client from './client';

export interface PermissionItem {
  id: string;
  name: string;
  description: string | null;
  resource: string;
  action: string;
  isSystem: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PermissionCreateRequest {
  name: string;
  description?: string;
  resource: string;
  action: string;
}

export interface PermissionUpdateRequest {
  name?: string;
  description?: string;
  resource?: string;
  action?: string;
}

export interface PageResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export async function listPermissions(page = 0, size = 20, resource?: string) {
  const params: Record<string, string | number> = { page, size };
  if (resource) params.resource = resource;
  const res = await client.get<PageResponse<PermissionItem>>('/api/admin/permissions', { params });
  return res.data;
}

export async function getPermission(id: string) {
  const res = await client.get<PermissionItem>(`/api/admin/permissions/${id}`);
  return res.data;
}

export async function createPermission(data: PermissionCreateRequest) {
  const res = await client.post<PermissionItem>('/api/admin/permissions', data);
  return res.data;
}

export async function updatePermission(id: string, data: PermissionUpdateRequest) {
  const res = await client.put<PermissionItem>(`/api/admin/permissions/${id}`, data);
  return res.data;
}

export async function deletePermission(id: string) {
  await client.delete(`/api/admin/permissions/${id}`);
}
```

Commit: `feat: add permissions API client`

---

### Task 2: Create roles API client

**Files:** Create `src/api/roles.ts`

```typescript
import client from './client';

export interface RoleItem {
  id: string;
  name: string;
  description: string | null;
  isSystem: boolean;
  permissions: string[];
  createdAt: string;
  updatedAt: string;
}

export interface RoleCreateRequest {
  name: string;
  description?: string;
}

export interface RoleUpdateRequest {
  name?: string;
  description?: string;
}

export interface RolePermissionUpdateRequest {
  permissionIds: string[];
}

export async function listRoles(page = 0, size = 20) {
  const res = await client.get<{ items: RoleItem[]; total: number; page: number; size: number }>('/api/admin/roles', {
    params: { page, size },
  });
  return res.data;
}

export async function getRole(id: string) {
  const res = await client.get<RoleItem>(`/api/admin/roles/${id}`);
  return res.data;
}

export async function createRole(data: RoleCreateRequest) {
  const res = await client.post<RoleItem>('/api/admin/roles', data);
  return res.data;
}

export async function updateRole(id: string, data: RoleUpdateRequest) {
  const res = await client.put<RoleItem>(`/api/admin/roles/${id}`, data);
  return res.data;
}

export async function deleteRole(id: string) {
  await client.delete(`/api/admin/roles/${id}`);
}

export async function getRolePermissions(id: string) {
  const res = await client.get<string[]>(`/api/admin/roles/${id}/permissions`);
  return res.data;
}

export async function updateRolePermissions(id: string, permissionIds: string[]) {
  await client.put(`/api/admin/roles/${id}/permissions`, { permissionIds });
}
```

Commit: `feat: add roles API client`

---

### Task 3: Create user roles API client

**Files:** Create `src/api/userRoles.ts`

```typescript
import client from './client';

export interface UserRoleItem {
  userId: string;
  roleId: string;
  roleName: string;
  roleDescription: string | null;
  assignedAt: string;
}

export async function getUserRoles(userId: string) {
  const res = await client.get<UserRoleItem[]>(`/api/admin/users/${userId}/roles`);
  return res.data;
}

export async function assignUserRole(userId: string, roleId: string) {
  const res = await client.post<UserRoleItem>(`/api/admin/users/${userId}/roles`, { roleId });
  return res.data;
}

export async function removeUserRole(userId: string, roleId: string) {
  await client.delete(`/api/admin/users/${userId}/roles/${roleId}`);
}

export async function updateUserRoles(userId: string, roleIds: string[]) {
  await client.put(`/api/admin/users/${userId}/roles`, { roleIds });
}
```

Commit: `feat: add user roles API client`

---

### Task 4: Create AdminPermissionsPage

**Files:** Create `src/react-app/pages/admin/AdminPermissionsPage.tsx`

Read existing admin pages (e.g., `AdminReviewListPage.tsx`) for styling patterns.

Build a page with:
- Table: name, resource, action, isSystem badge, actions (edit/delete)
- Resource filter dropdown
- Create button → dialog with form (name, description, resource, action)
- Edit button → dialog with pre-filled form
- Delete button → confirmation dialog (disabled for system permissions)
- Pagination

Use Tailwind CSS classes matching existing admin pages. Use `lucide-react` icons (Shield, Plus, Pencil, Trash2).

Commit: `feat: add AdminPermissionsPage with CRUD`

---

### Task 5: Create AdminRolesPage

**Files:** Create `src/react-app/pages/admin/AdminRolesPage.tsx`

Build a page with:
- Table: name, description, isSystem badge, permission count, actions
- Create button → dialog (name, description)
- Edit button → dialog (name, description)
- Delete button → confirmation (disabled for system roles)
- Expandable row or detail dialog showing permission checkboxes
- Permission assignment: checkbox grid grouped by resource, with save button
- Fetch all permissions (unpaginated) for the checkbox grid

Commit: `feat: add AdminRolesPage with CRUD and permission assignment`

---

### Task 6: Update AdminLayout sidebar

**Files:** Modify `src/react-app/pages/admin/AdminLayout.tsx`

Read the current file to find `NAV_ITEMS`. Add entries:

```typescript
{ label: 'Roles', path: '/admin/roles', icon: Users },
{ label: 'Permissions', path: '/admin/permissions', icon: Shield },
```

Import `Users` and `Shield` from `lucide-react`.

Commit: `feat: add RBAC nav items to admin sidebar`

---

### Task 7: Add routes in App.tsx

**Files:** Modify `src/react-app/App.tsx`

Add lazy imports and routes:

```typescript
const AdminPermissionsPage = lazy(() => import('./pages/admin/AdminPermissionsPage'));
const AdminRolesPage = lazy(() => import('./pages/admin/AdminRolesPage'));
```

Add routes inside the admin route group:

```tsx
<Route path="/admin/permissions" element={<AdminPermissionsPage />} />
<Route path="/admin/roles" element={<AdminRolesPage />} />
```

Commit: `feat: add RBAC routes to App.tsx`

---

### Task 8: Final build verification

Run: `npm run build`

Fix any TypeScript errors.

Commit: `chore: verify RBAC frontend build`
