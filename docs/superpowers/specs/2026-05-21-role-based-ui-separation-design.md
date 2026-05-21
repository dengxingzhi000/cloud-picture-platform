# Role-Based UI Separation Design

## Overview

This design implements role-based UI separation for the Cloud Picture Platform, providing distinct interfaces for different user roles: Regular Users, Moderators, and Administrators.

## Goals

- Each role has focused, specialized functionality
- Avoid redundant or overwhelming UI elements
- Dynamic menu rendering based on backend configuration
- Secure permission control at both frontend and backend levels

## Architecture

### Role Definitions

| Role | Description | Entry Point |
|------|-------------|-------------|
| ROLE_USER | Regular user - manage own pictures, albums, teams | /user/* or / |
| ROLE_MODERATOR | Content moderator - review submissions | /admin/* |
| ROLE_ADMIN | System administrator - full management access | /admin/* |

### UI Separation Strategy

- **Regular Users**: Independent entry point (`/user` or `/`)
  - My Pictures
  - My Albums
  - My Teams
  - Personal Settings

- **Moderators & Administrators**: Shared admin portal (`/admin`)
  - Dynamic sidebar menu based on role
  - Moderators see: Content Review, Review History
  - Admins see: All menus (User Management, Role Management, System Settings, etc.)

## Database Design

### New Tables

```sql
-- Menu table (tree structure)
CREATE TABLE menu (
    id          UUID PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,     -- Display name (e.g., "Content Review")
    code        VARCHAR(50) NOT NULL,     -- Menu code (e.g., "content_review")
    parent_id   UUID,                     -- Parent menu ID (supports multi-level)
    path        VARCHAR(100),             -- Frontend route path (e.g., "/admin/reviews")
    icon        VARCHAR(50),              -- Icon name
    sort_order  INT NOT NULL DEFAULT 0,   -- Sort order
    is_visible  BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_menu_code UNIQUE (code)
);

-- Role-Menu junction table
CREATE TABLE role_menu (
    role_id    UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    menu_id    UUID NOT NULL REFERENCES menu(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, menu_id)
);
```

### Seed Data

**Initial Menus:**

| Name | Code | Path | Visible To |
|------|------|------|------------|
| Content Review | content_review | /admin/reviews | Moderator, Admin |
| Review History | review_history | /admin/reviews/history | Moderator, Admin |
| User Management | user_management | /admin/users | Admin |
| Role Management | role_management | /admin/roles | Admin |
| Permission Management | permission_management | /admin/permissions | Admin |
| System Settings | system_settings | /admin/settings | Admin |

## API Design

### Login Endpoint

```
POST /api/auth/login
Request:
{
  "username": "admin",
  "password": "xxx"
}

Response:
{
  "token": "jwt_token_xxx",
  "userInfo": {
    "id": "uuid",
    "username": "admin",
    "displayName": "Administrator",
    "avatarUrl": "...",
    "roles": ["ROLE_ADMIN"]
  },
  "menus": [
    {
      "id": "uuid",
      "name": "Content Review",
      "code": "content_review",
      "path": "/admin/reviews",
      "icon": "shield-check",
      "children": [
        {
          "id": "uuid",
          "name": "Review History",
          "path": "/admin/reviews/history"
        }
      ]
    }
  ],
  "permissions": [
    "audit:approve",
    "audit:reject",
    "user:create",
    "user:delete"
  ]
}
```

### Current User Endpoint

```
GET /api/auth/me
Headers: Authorization: Bearer {token}

Response: (same structure as login response, without token)
{
  "userInfo": { ... },
  "menus": [ ... ],
  "permissions": [ ... ]
}
```

### Key Design Decisions

1. **`/api/auth/me` is required**: When page refreshes, Context is lost. Use localStorage token to re-fetch user data.

2. **menus and permissions NOT stored in localStorage**: Prevent tampering. Always fetch from server.

3. **Route guard logic**: Based on backend menus list. If moderator's menus don't include `/users`, direct URL access will be blocked with 403.

4. **Backend SQL structure is isomorphic with frontend MenuItem interface**: `/api/auth/login` and `/api/auth/me` JOIN `sys_role_menu` and return tree structure directly.

## Frontend Design (React)

### Technology Stack

- React 19 + TypeScript + Vite
- React Router DOM v7
- React Context (no Redux/Zustand)
- Tailwind CSS + lucide-react

### New Files

```
src/
├── api/
│   └── menus.ts           # Get menus API
├── react-app/
│   ├── menu.tsx            # MenuProvider
│   ├── permission.tsx      # PermissionProvider
│   └── components/
│       └── Permission.tsx  # Permission button component
```

### Modified Files

```
src/
├── api/
│   └── auth.ts            # Login returns menus + permissions
├── react-app/
│   ├── auth.tsx           # Store menus + permissions in context
│   ├── App.tsx            # Enhanced route guards
│   └── pages/
│       └── admin/
│           └── AdminLayout.tsx  # Dynamic sidebar
```

### Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│  Page Refresh Flow                                          │
├─────────────────────────────────────────────────────────────┤
│  1. Context lost, read token from localStorage              │
│  2. Call GET /api/auth/me (with token)                      │
│  3. Server returns { userInfo, menus, permissions }         │
│  4. Context restored, page renders normally                 │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Login Flow                                                 │
├─────────────────────────────────────────────────────────────┤
│  1. POST /api/auth/login                                    │
│  2. Returns { token, userInfo, menus, permissions }         │
│  3. token → localStorage                                    │
│  4. menus + permissions → Context (NOT in localStorage)     │
└─────────────────────────────────────────────────────────────┘
```

### Security Design

| Data | Storage | Reason |
|------|---------|--------|
| token | localStorage | Needs persistence for page refresh |
| userInfo | localStorage | Basic user info, can be persisted |
| menus | Context only | Prevent tampering, fetch from server each refresh |
| permissions | Context only | Prevent tampering, fetch from server each refresh |

### Route Guard Implementation

```typescript
function ProtectedRoute({ children }) {
  const { menus } = useMenu()
  const location = useLocation()
  
  // Extract all accessible paths from menus
  const allowedPaths = flattenMenuPaths(menus)
  
  // Check if current path is in allowed list
  if (!allowedPaths.some(path => 
    location.pathname.startsWith(path)
  )) {
    return <Navigate to="/403" replace />
  }
  
  return children
}
```

### Permission Button Component

```typescript
// src/components/Permission.tsx
export function Permission({ 
  permission, 
  children 
}: { 
  permission: string
  children: ReactNode 
}) {
  const { hasPermission } = usePermission()
  
  if (!hasPermission(permission)) return null
  return <>{children}</>
}
```

**Usage Example:**

```tsx
<Permission permission="audit:approve">
  <Button onClick={approve}>Approve</Button>
</Permission>
```

### AdminLayout Dynamic Sidebar

```typescript
// Replace hardcoded NAV_ITEMS with dynamic menus
const { menus } = useMenu()

<nav>
  {menus.map((item) => (
    <NavLink key={item.id} to={item.path}>
      <span>{item.icon}</span>
      <span>{item.name}</span>
    </NavLink>
  ))}
</nav>
```

## Permission Control Flow

```
┌─────────────────────────────────────────────────────────────┐
│  1. POST /api/auth/login                                    │
│     → Returns token + userInfo + menus + permissions        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  2. Frontend Storage                                        │
│     → token → localStorage/cookie                           │
│     → menus + permissions → Context                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  3. Dynamic Sidebar Rendering (consume menus)               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  4. Route Guard                                             │
│     → Verify token validity                                 │
│     → Verify target route is in menus permission            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  5. Page-level Button Control (consume permissions)         │
│     → v-if="hasPermission('audit:approve')"                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  6. Backend Independent Auth (fallback, cannot skip)        │
│     → @PreAuthorize("hasAuthority('audit:approve')")        │
└─────────────────────────────────────────────────────────────┘
```

## Success Criteria

1. Regular users cannot access admin routes
2. Moderators only see review-related menus
3. Administrators see all menus
4. Button-level permissions work correctly
5. Page refresh restores full context
6. Direct URL access is properly guarded
