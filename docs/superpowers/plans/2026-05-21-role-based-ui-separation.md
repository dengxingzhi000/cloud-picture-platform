# Role-Based UI Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement role-based UI separation with dynamic menus, route guards, and button-level permissions for Regular Users, Moderators, and Administrators.

**Architecture:** Backend returns menus and permissions on login/me endpoints. Frontend stores in Context (not localStorage) for security. Route guards validate against menus list. AdminLayout renders sidebar dynamically.

**Tech Stack:** Spring Boot 4.0.5, Java 21, PostgreSQL, Flyway, React 19, TypeScript, Vite, React Router DOM v7, Tailwind CSS

---

## File Structure

### Backend Files

| File | Purpose |
|------|---------|
| `src/main/resources/db/migration/V27__menu.sql` | Create menu and role_menu tables with seed data |
| `src/main/java/com/cn/cloudpictureplatform/domain/rbac/Menu.java` | Menu entity |
| `src/main/java/com/cn/cloudpictureplatform/domain/rbac/RoleMenu.java` | Role-Menu junction entity |
| `src/main/java/com/cn/cloudpictureplatform/infrastructure/persistence/MenuRepository.java` | Menu repository |
| `src/main/java/com/cn/cloudpictureplatform/application/auth/dto/MenuItemResponse.java` | Menu item DTO |
| `src/main/java/com/cn/cloudpictureplatform/application/auth/AuthService.java` | Modify: add menus+permissions to login/me response |

### Frontend Files

| File | Purpose |
|------|---------|
| `src/api/menus.ts` | Menu API types (menus returned by login/me) |
| `src/react-app/menu.tsx` | MenuProvider context |
| `src/react-app/permission.tsx` | PermissionProvider context |
| `src/react-app/components/Permission.tsx` | Permission button component |
| `src/react-app/auth.tsx` | Modify: store menus+permissions |
| `src/react-app/App.tsx` | Modify: enhanced route guards |
| `src/react-app/pages/admin/AdminLayout.tsx` | Modify: dynamic sidebar |

---

### Task 1: Database Migration - Create Menu Tables

**Files:**
- Create: `src/main/resources/db/migration/V27__menu.sql`

- [ ] **Step 1: Create migration file**

```sql
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
```

- [ ] **Step 2: Verify migration runs**

Run: `.\mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V27__menu.sql
git commit -m "feat(db): add menu and role_menu tables with seed data"
```

---

### Task 2: Backend - Create Menu Entity and Repository

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/domain/rbac/Menu.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/domain/rbac/RoleMenu.java`
- Create: `src/main/java/com/cn/cloudpictureplatform/infrastructure/persistence/MenuRepository.java`

- [ ] **Step 1: Create Menu entity**

```java
package com.cn.cloudpictureplatform.domain.rbac;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "menu")
public class Menu extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(length = 100)
    private String path;

    @Column(length = 50)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_visible", nullable = false)
    @Builder.Default
    private Boolean isVisible = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private Menu parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<Menu> children = new ArrayList<>();
}
```

- [ ] **Step 2: Create RoleMenu entity**

```java
package com.cn.cloudpictureplatform.domain.rbac;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
class RoleMenuKey implements Serializable {
    @Column(name = "role_id", columnDefinition = "uuid")
    private UUID roleId;

    @Column(name = "menu_id", columnDefinition = "uuid")
    private UUID menuId;
}

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "role_menu")
@IdClass(RoleMenuKey.class)
public class RoleMenu {

    @Id
    @Column(name = "role_id", columnDefinition = "uuid", nullable = false)
    private UUID roleId;

    @Id
    @Column(name = "menu_id", columnDefinition = "uuid", nullable = false)
    private UUID menuId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", insertable = false, updatable = false)
    private Menu menu;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
```

- [ ] **Step 3: Create MenuRepository**

```java
package com.cn.cloudpictureplatform.infrastructure.persistence;

import com.cn.cloudpictureplatform.domain.rbac.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MenuRepository extends JpaRepository<Menu, UUID> {

    @Query("""
        SELECT DISTINCT m FROM Menu m
        JOIN RoleMenu rm ON rm.menuId = m.id
        WHERE rm.roleId IN :roleIds
        AND m.isVisible = true
        ORDER BY m.sortOrder ASC
    """)
    List<Menu> findByRoleIds(@Param("roleIds") List<UUID> roleIds);

    List<Menu> findByParentIdIsNullOrderBySortOrderAsc();
}
```

- [ ] **Step 4: Verify compilation**

Run: `.\mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/domain/rbac/Menu.java
git add src/main/java/com/cn/cloudpictureplatform/domain/rbac/RoleMenu.java
git add src/main/java/com/cn/cloudpictureplatform/infrastructure/persistence/MenuRepository.java
git commit -m "feat: add Menu and RoleMenu entities with repository"
```

---

### Task 3: Backend - Create Menu DTO and Update AuthService

**Files:**
- Create: `src/main/java/com/cn/cloudpictureplatform/application/auth/dto/MenuItemResponse.java`
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/auth/AuthService.java`

- [ ] **Step 1: Create MenuItemResponse DTO**

```java
package com.cn.cloudpictureplatform.application.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemResponse {
    private UUID id;
    private String name;
    private String code;
    private String path;
    private String icon;
    private Integer sortOrder;
    @Builder.Default
    private List<MenuItemResponse> children = new ArrayList<>();
}
```

- [ ] **Step 2: Read current AuthService**

Read `src/main/java/com/cn/cloudpictureplatform/application/auth/AuthService.java` to understand current structure.

- [ ] **Step 3: Add menu query method to AuthService**

Add import and inject `MenuRepository`:

```java
import com.cn.cloudpictureplatform.infrastructure.persistence.MenuRepository;
import com.cn.cloudpictureplatform.application.auth.dto.MenuItemResponse;
```

Add field:
```java
private final MenuRepository menuRepository;
```

Add method:
```java
public List<MenuItemResponse> getMenusForRoles(List<UUID> roleIds) {
    List<Menu> menus = menuRepository.findByRoleIds(roleIds);
    return buildMenuTree(menus, null);
}

private List<MenuItemResponse> buildMenuTree(List<Menu> menus, UUID parentId) {
    return menus.stream()
            .filter(m -> (parentId == null && m.getParentId() == null) ||
                         (parentId != null && parentId.equals(m.getParentId())))
            .map(m -> MenuItemResponse.builder()
                    .id(m.getId())
                    .name(m.getName())
                    .code(m.getCode())
                    .path(m.getPath())
                    .icon(m.getIcon())
                    .sortOrder(m.getSortOrder())
                    .children(buildMenuTree(menus, m.getId()))
                    .build())
            .toList();
}
```

- [ ] **Step 4: Update login response to include menus**

Modify the login method to include menus in the response. The exact location depends on current AuthService implementation.

- [ ] **Step 5: Verify compilation**

Run: `.\mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/auth/dto/MenuItemResponse.java
git add src/main/java/com/cn/cloudpictureplatform/application/auth/AuthService.java
git commit -m "feat(auth): add menus to login and me endpoints"
```

---

### Task 4: Backend - Update Auth API Response

**Files:**
- Modify: `src/main/java/com/cn/cloudpictureplatform/application/auth/dto/AuthResponse.java` (or equivalent)
- Modify: `src/main/java/com/cn/cloudpictureplatform/interfaces/auth/AuthController.java`

- [ ] **Step 1: Read current AuthResponse**

Read the current auth response DTO to understand its structure.

- [ ] **Step 2: Add menus and permissions fields**

Add to AuthResponse or create new response:

```java
private List<MenuItemResponse> menus;
private List<String> permissions;
```

- [ ] **Step 3: Update login endpoint**

Modify login endpoint to return menus and permissions:

```java
@PostMapping("/login")
public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
    // ... existing auth logic ...
    
    // Get user roles
    List<UUID> roleIds = userRoleRepository.findByUserId(user.getId())
            .stream()
            .map(UserRole::getRoleId)
            .toList();
    
    // Get menus for roles
    List<MenuItemResponse> menus = authService.getMenusForRoles(roleIds);
    
    // Get permissions for roles
    List<String> permissions = permissionService.getPermissionsForRoles(roleIds);
    
    return ApiResponse.ok(LoginResponse.builder()
            .token(token)
            .userInfo(userInfo)
            .menus(menus)
            .permissions(permissions)
            .build());
}
```

- [ ] **Step 4: Update /api/auth/me endpoint**

Similar changes to return menus and permissions.

- [ ] **Step 5: Verify compilation**

Run: `.\mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/cn/cloudpictureplatform/application/auth/
git add src/main/java/com/cn/cloudpictureplatform/interfaces/auth/
git commit -m "feat(auth): return menus and permissions in login/me responses"
```

---

### Task 5: Frontend - Create Menu and Permission Contexts

**Files:**
- Create: `src/react-app/menu.tsx`
- Create: `src/react-app/permission.tsx`

- [ ] **Step 1: Create MenuProvider**

```typescript
// src/react-app/menu.tsx
import { createContext, useContext, useMemo, useState, type PropsWithChildren } from 'react'

export type MenuItem = {
  id: string
  name: string
  code: string
  path: string | null
  icon: string | null
  sortOrder: number
  children?: MenuItem[]
}

type MenuContextValue = {
  menus: MenuItem[]
  setMenus: (menus: MenuItem[]) => void
  flattenMenuPaths: (items?: MenuItem[]) => string[]
}

const MenuContext = createContext<MenuContextValue | null>(null)

export function MenuProvider({ children }: PropsWithChildren) {
  const [menus, setMenus] = useState<MenuItem[]>([])

  const flattenMenuPaths = (items?: MenuItem[]): string[] => {
    const result: string[] = []
    const traverse = (list: MenuItem[]) => {
      for (const item of list) {
        if (item.path) result.push(item.path)
        if (item.children?.length) traverse(item.children)
      }
    }
    traverse(items ?? menus)
    return result
  }

  const value = useMemo<MenuContextValue>(
    () => ({ menus, setMenus, flattenMenuPaths }),
    [menus]
  )

  return <MenuContext.Provider value={value}>{children}</MenuContext.Provider>
}

export function useMenu() {
  const context = useContext(MenuContext)
  if (!context) {
    throw new Error('useMenu must be used inside MenuProvider')
  }
  return context
}
```

- [ ] **Step 2: Create PermissionProvider**

```typescript
// src/react-app/permission.tsx
import { createContext, useContext, useMemo, useState, type PropsWithChildren } from 'react'

type PermissionContextValue = {
  permissions: string[]
  setPermissions: (permissions: string[]) => void
  hasPermission: (code: string) => boolean
}

const PermissionContext = createContext<PermissionContextValue | null>(null)

export function PermissionProvider({ children }: PropsWithChildren) {
  const [permissions, setPermissions] = useState<string[]>([])

  const hasPermission = (code: string) => permissions.includes(code)

  const value = useMemo<PermissionContextValue>(
    () => ({ permissions, setPermissions, hasPermission }),
    [permissions]
  )

  return <PermissionContext.Provider value={value}>{children}</PermissionContext.Provider>
}

export function usePermission() {
  const context = useContext(PermissionContext)
  if (!context) {
    throw new Error('usePermission must be used inside PermissionProvider')
  }
  return context
}
```

- [ ] **Step 3: Verify TypeScript compilation**

Run: `npm run build` (in frontend project)
Expected: No TypeScript errors

- [ ] **Step 4: Commit**

```bash
git add src/react-app/menu.tsx src/react-app/permission.tsx
git commit -m "feat: add MenuProvider and PermissionProvider contexts"
```

---

### Task 6: Frontend - Create Permission Component

**Files:**
- Create: `src/react-app/components/Permission.tsx`

- [ ] **Step 1: Create Permission component**

```typescript
// src/react-app/components/Permission.tsx
import type { ReactNode } from 'react'
import { usePermission } from '@/react-app/permission'

type PermissionProps = {
  permission: string
  children: ReactNode
  fallback?: ReactNode
}

export function Permission({ permission, children, fallback = null }: PermissionProps) {
  const { hasPermission } = usePermission()

  if (!hasPermission(permission)) {
    return <>{fallback}</>
  }

  return <>{children}</>
}
```

- [ ] **Step 2: Verify TypeScript compilation**

Run: `npm run build`
Expected: No TypeScript errors

- [ ] **Step 3: Commit**

```bash
git add src/react-app/components/Permission.tsx
git commit -m "feat: add Permission button component"
```

---

### Task 7: Frontend - Update AuthProvider

**Files:**
- Modify: `src/react-app/auth.tsx`

- [ ] **Step 1: Read current auth.tsx**

Already read. Key changes needed:
- Import MenuProvider and PermissionProvider
- Store menus and permissions from login/me response
- Expose menus and permissions in context

- [ ] **Step 2: Update AuthProvider**

```typescript
// Add imports
import { MenuProvider, useMenu, type MenuItem } from '@/react-app/menu'
import { PermissionProvider, usePermission } from '@/react-app/permission'

// Update AuthContextValue type
type AuthContextValue = {
  user: StoredUser | null
  isAuthed: boolean
  isAdmin: boolean
  ready: boolean
  refresh: () => Promise<void>
  applyAuth: (token: string, nextUser: StoredUser, menus?: MenuItem[], permissions?: string[]) => void
  logout: () => void
}

// Update AuthProvider to use MenuProvider and PermissionProvider
export function AuthProvider({ children }: PropsWithChildren) {
  return (
    <MenuProvider>
      <PermissionProvider>
        <AuthProviderInner>{children}</AuthProviderInner>
      </PermissionProvider>
    </MenuProvider>
  )
}

function AuthProviderInner({ children }: PropsWithChildren) {
  const { setMenus } = useMenu()
  const { setPermissions } = usePermission()
  const [user, setUserState] = useState<StoredUser | null>(() => getUser())
  const [ready, setReady] = useState(false)

  async function refresh() {
    if (!getToken()) {
      setUserState(null)
      setMenus([])
      setPermissions([])
      setReady(true)
      return
    }
    try {
      const me = await fetchMe()
      setUser(me.user)
      setUserState(me.user)
      setMenus(me.menus)
      setPermissions(me.permissions)
    } catch {
      clearToken()
      clearUser()
      setUserState(null)
      setMenus([])
      setPermissions([])
    } finally {
      setReady(true)
    }
  }

  function applyAuth(token: string, nextUser: StoredUser, menus?: MenuItem[], permissions?: string[]) {
    setToken(token)
    setUser(nextUser)
    setUserState(nextUser)
    if (menus) setMenus(menus)
    if (permissions) setPermissions(permissions)
    setReady(true)
  }

  function logout() {
    clearToken()
    clearUser()
    setUserState(null)
    setMenus([])
    setPermissions([])
    setReady(true)
  }

  useEffect(() => {
    void refresh()
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthed: Boolean(getToken()),
      isAdmin: user?.role === 'ADMIN',
      ready,
      refresh,
      applyAuth,
      logout,
    }),
    [ready, user]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
```

- [ ] **Step 3: Update fetchMe to return menus and permissions**

Update `src/api/auth.ts` to return full response including menus and permissions.

- [ ] **Step 4: Verify TypeScript compilation**

Run: `npm run build`
Expected: No TypeScript errors

- [ ] **Step 5: Commit**

```bash
git add src/react-app/auth.tsx src/api/auth.ts
git commit -m "feat(auth): integrate menus and permissions into AuthProvider"
```

---

### Task 8: Frontend - Update Login Page

**Files:**
- Modify: `src/react-app/pages/LoginPage.tsx` (or wherever login is handled)

- [ ] **Step 1: Read current login page**

Find and read the login page component.

- [ ] **Step 2: Update login handler**

```typescript
const handleLogin = async (username: string, password: string) => {
  const response = await login({ username, password })
  // response now includes token, userInfo, menus, permissions
  applyAuth(response.token, response.userInfo, response.menus, response.permissions)
  navigate('/gallery')
}
```

- [ ] **Step 3: Verify TypeScript compilation**

Run: `npm run build`
Expected: No TypeScript errors

- [ ] **Step 4: Commit**

```bash
git add src/react-app/pages/LoginPage.tsx
git commit -m "feat(login): handle menus and permissions from login response"
```

---

### Task 9: Frontend - Update Route Guards

**Files:**
- Modify: `src/react-app/App.tsx`

- [ ] **Step 1: Update RequireAdmin to use menus**

```typescript
function RequireAdmin() {
  const { ready } = useAuth()
  const { menus, flattenMenuPaths } = useMenu()
  const location = useLocation()
  const { t } = useTranslation()

  if (!ready) {
    return <div className="page"><section className="panel">{t('common.loading')}</section></div>
  }

  // Check if current path is in allowed menu paths
  const allowedPaths = flattenMenuPaths()
  const isAllowed = allowedPaths.some(path => 
    location.pathname === path || location.pathname.startsWith(path + '/')
  )

  if (!isAllowed) {
    return <Navigate to="/403" replace />
  }

  return <Outlet />
}
```

- [ ] **Step 2: Add 403 page**

Create a simple 403 Forbidden page component.

- [ ] **Step 3: Update route structure**

Ensure admin routes use the updated RequireAdmin guard.

- [ ] **Step 4: Verify TypeScript compilation**

Run: `npm run build`
Expected: No TypeScript errors

- [ ] **Step 5: Commit**

```bash
git add src/react-app/App.tsx
git commit -m "feat(routes): implement menu-based route guards"
```

---

### Task 10: Frontend - Update AdminLayout Dynamic Sidebar

**Files:**
- Modify: `src/react-app/pages/admin/AdminLayout.tsx`

- [ ] **Step 1: Replace hardcoded NAV_ITEMS with dynamic menus**

```typescript
import { useMenu, type MenuItem } from '@/react-app/menu'

export default function AdminLayout() {
  const { menus } = useMenu()
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [collapsed, setCollapsed] = useState(false)

  // ... existing code ...

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg-base)' }}>
      {/* Sidebar */}
      <aside>
        {/* Brand */}
        {/* ... existing brand code ... */}

        {/* Dynamic Navigation */}
        <nav style={{ flex: 1, padding: '12px 8px', display: 'flex', flexDirection: 'column', gap: 2 }}>
          {menus.map((item) => (
            <NavLink
              key={item.id}
              to={item.path || '#'}
              end={item.path === '/admin'}
              style={({ isActive }) => ({
                /* ... existing styles ... */
              })}
            >
              <span style={{ fontSize: '1.1rem', flexShrink: 0, width: 20, textAlign: 'center' }}>
                {item.icon || '•'}
              </span>
              {!collapsed && <span>{item.name}</span>}
            </NavLink>
          ))}
        </nav>

        {/* ... existing collapse toggle ... */}
      </aside>

      {/* Main area */}
      {/* ... existing main area code ... */}
    </div>
  )
}
```

- [ ] **Step 2: Verify TypeScript compilation**

Run: `npm run build`
Expected: No TypeScript errors

- [ ] **Step 3: Commit**

```bash
git add src/react-app/pages/admin/AdminLayout.tsx
git commit -m "feat(admin): render dynamic sidebar from menus"
```

---

### Task 11: Integration Testing

- [ ] **Step 1: Start backend**

Run: `.\mvnw spring-boot:run --spring.profiles.active=dev`

- [ ] **Step 2: Start frontend**

Run: `npm run dev`

- [ ] **Step 3: Test login as admin**

1. Login with admin credentials
2. Verify menus include all admin menus
3. Verify sidebar renders dynamically
4. Navigate to /admin/users - should work

- [ ] **Step 4: Test login as moderator**

1. Login with moderator credentials
2. Verify menus only include Dashboard and Content Review
3. Navigate to /admin/users - should redirect to 403

- [ ] **Step 5: Test page refresh**

1. Login and navigate to admin page
2. Refresh page (F5)
3. Verify menus and permissions are restored from /api/auth/me

- [ ] **Step 6: Test button permissions**

1. Add Permission component to a test button
2. Verify button shows/hides based on user permissions

- [ ] **Step 7: Commit final changes**

```bash
git add .
git commit -m "feat: complete role-based UI separation implementation"
```

---

## Success Criteria Verification

| Criteria | Test Method |
|----------|-------------|
| Regular users cannot access admin routes | Login as user, try /admin |
| Moderators only see review-related menus | Login as moderator, check sidebar |
| Administrators see all menus | Login as admin, check sidebar |
| Button-level permissions work | Use Permission component |
| Page refresh restores context | Refresh on admin page |
| Direct URL access is guarded | Type /admin/users as moderator |
