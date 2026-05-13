# RBAC Management API Development Plan

## 1. Overview

This document outlines the development plan for adding Role-Based Access Control (RBAC) management APIs to the Cloud Picture Platform. The current system has RBAC tables and seed data in place, but lacks administrative APIs for managing roles, permissions, and user-role assignments.

## 2. Current State

### 2.1 Existing RBAC Infrastructure

**Database Tables (V2__rbac.sql):**
- `role` - Stores role definitions (ROLE_USER, ROLE_MODERATOR, ROLE_ADMIN)
- `permission` - Stores permission definitions (resource:action pairs)
- `user_role` - Many-to-many relationship between users and roles
- `role_permission` - Many-to-many relationship between roles and permissions

**Seed Data:**
- 3 roles: ROLE_USER, ROLE_MODERATOR, ROLE_ADMIN
- 17 permissions across picture, tag, team, and admin resources
- Role-permission mappings for all three roles

**Existing Security Components:**
- `SecurityConfig` - HTTP security rules and endpoint protection
- `JwtAuthenticationFilter` - JWT token validation
- `AppUserDetailsService` - User authentication with RBAC
- `AppUserPrincipal` - User principal with roles and permissions
- `JwtTokenService` - JWT token generation and validation

### 2.2 Current Limitations

1. **No Role Management API** - Cannot create, update, or delete roles
2. **No Permission Management API** - Cannot manage permissions
3. **No User-Role Assignment API** - Cannot assign/remove roles from users
4. **No Role-Permission Assignment API** - Cannot manage role permissions
5. **Limited Admin Capabilities** - Only picture moderation and search reindexing

## 3. Development Goals

### 3.1 Primary Objectives

1. **Role Management** - CRUD operations for roles
2. **Permission Management** - CRUD operations for permissions
3. **User-Role Assignment** - Assign/remove roles for users
4. **Role-Permission Assignment** - Manage permissions for roles
5. **Admin Interface** - RESTful APIs for administrators

### 3.2 Secondary Objectives

1. **Role Hierarchy** - Support role inheritance (optional)
2. **Permission Grouping** - Organize permissions by resource
3. **Audit Logging** - Track RBAC changes
4. **Bulk Operations** - Batch assign/remove roles

## 4. Technical Design

### 4.1 API Endpoints

#### 4.1.1 Role Management

```
GET    /api/admin/roles                    - List all roles
GET    /api/admin/roles/{id}               - Get role details
POST   /api/admin/roles                    - Create new role
PUT    /api/admin/roles/{id}               - Update role
DELETE /api/admin/roles/{id}               - Delete role
GET    /api/admin/roles/{id}/permissions   - Get role permissions
PUT    /api/admin/roles/{id}/permissions   - Update role permissions
```

#### 4.1.2 Permission Management

```
GET    /api/admin/permissions              - List all permissions
GET    /api/admin/permissions/{id}         - Get permission details
POST   /api/admin/permissions              - Create new permission
PUT    /api/admin/permissions/{id}         - Update permission
DELETE /api/admin/permissions/{id}         - Delete permission
```

#### 4.1.3 User-Role Assignment

```
GET    /api/admin/users/{userId}/roles     - Get user roles
POST   /api/admin/users/{userId}/roles     - Assign role to user
DELETE /api/admin/users/{userId}/roles/{roleId} - Remove role from user
PUT    /api/admin/users/{userId}/roles     - Update user roles (bulk)
```

### 4.2 Data Transfer Objects (DTOs)

#### 4.2.1 Role DTOs

```java
// RoleResponse
public class RoleResponse {
    private UUID id;
    private String name;
    private String description;
    private Set<String> permissions;
    private Instant createdAt;
    private Instant updatedAt;
}

// RoleCreateRequest
public class RoleCreateRequest {
    @NotBlank
    @Size(max = 50)
    private String name;
    
    @Size(max = 200)
    private String description;
}

// RoleUpdateRequest
public class RoleUpdateRequest {
    @Size(max = 50)
    private String name;
    
    @Size(max = 200)
    private String description;
}
```

#### 4.2.2 Permission DTOs

```java
// PermissionResponse
public class PermissionResponse {
    private UUID id;
    private String name;
    private String description;
    private String resource;
    private String action;
    private Instant createdAt;
    private Instant updatedAt;
}

// PermissionCreateRequest
public class PermissionCreateRequest {
    @NotBlank
    @Size(max = 100)
    private String name;
    
    @Size(max = 200)
    private String description;
    
    @NotBlank
    @Size(max = 50)
    private String resource;
    
    @NotBlank
    @Size(max = 50)
    private String action;
}
```

#### 4.2.3 User-Role Assignment DTOs

```java
// UserRoleResponse
public class UserRoleResponse {
    private UUID userId;
    private UUID roleId;
    private String roleName;
    private Instant assignedAt;
}

// UserRoleAssignRequest
public class UserRoleAssignRequest {
    @NotNull
    private UUID roleId;
}

// UserRoleBulkUpdateRequest
public class UserRoleBulkUpdateRequest {
    @NotEmpty
    private Set<UUID> roleIds;
}
```

### 4.3 Service Layer Design

#### 4.3.1 RoleService

```java
@Service
@Transactional
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    
    // CRUD operations
    public RoleResponse createRole(RoleCreateRequest request);
    public RoleResponse getRole(UUID roleId);
    public PageResponse<RoleResponse> listRoles(int page, int size);
    public RoleResponse updateRole(UUID roleId, RoleUpdateRequest request);
    public void deleteRole(UUID roleId);
    
    // Permission management
    public Set<PermissionResponse> getRolePermissions(UUID roleId);
    public void updateRolePermissions(UUID roleId, Set<UUID> permissionIds);
}
```

#### 4.3.2 PermissionService

```java
@Service
@Transactional
public class PermissionService {
    private final PermissionRepository permissionRepository;
    
    // CRUD operations
    public PermissionResponse createPermission(PermissionCreateRequest request);
    public PermissionResponse getPermission(UUID permissionId);
    public PageResponse<PermissionResponse> listPermissions(int page, int size, String resource);
    public PermissionResponse updatePermission(UUID permissionId, PermissionCreateRequest request);
    public void deletePermission(UUID permissionId);
}
```

#### 4.3.3 UserRoleService

```java
@Service
@Transactional
public class UserRoleService {
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final AppUserRepository userRepository;
    
    // User role management
    public Set<UserRoleResponse> getUserRoles(UUID userId);
    public UserRoleResponse assignRole(UUID userId, UUID roleId);
    public void removeRole(UUID userId, UUID roleId);
    public void updateUserRoles(UUID userId, Set<UUID> roleIds);
}
```

### 4.4 Security Considerations

#### 4.4.1 Permission Requirements

All RBAC management endpoints should require `ROLE_ADMIN` role or specific permissions:

- `admin:role` - Manage roles
- `admin:permission` - Manage permissions
- `admin:user-role` - Manage user-role assignments

#### 4.4.2 Validation Rules

1. **Role Name Uniqueness** - Role names must be unique
2. **Permission Uniqueness** - Resource + Action combination must be unique
3. **Protected Roles** - System roles (ROLE_USER, ROLE_ADMIN) cannot be deleted
4. **Protected Permissions** - Core permissions cannot be deleted
5. **Last Admin Protection** - Cannot remove last ADMIN role from system

#### 4.4.3 Audit Logging

All RBAC changes should be logged:
- Who made the change
- What was changed
- When it was changed
- Previous and new values

## 5. Implementation Plan

### 5.1 Phase 1: Core Infrastructure (Estimated: 2-3 days)

1. **Create DTO classes**
   - RoleResponse, RoleCreateRequest, RoleUpdateRequest
   - PermissionResponse, PermissionCreateRequest
   - UserRoleResponse, UserRoleAssignRequest

2. **Create Repository enhancements**
   - Add custom queries if needed
   - Add pagination support

3. **Create Service classes**
   - RoleService with CRUD operations
   - PermissionService with CRUD operations
   - UserRoleService with assignment operations

### 5.2 Phase 2: API Controllers (Estimated: 2-3 days)

1. **Create AdminRoleController**
   - Role CRUD endpoints
   - Role-permission management endpoints

2. **Create AdminPermissionController**
   - Permission CRUD endpoints

3. **Create AdminUserRoleController**
   - User-role assignment endpoints

4. **Update SecurityConfig**
   - Add new endpoint permissions
   - Add new permission constants

### 5.3 Phase 3: Validation and Security (Estimated: 1-2 days)

1. **Add validation logic**
   - Unique constraint validation
   - Protected resource validation
   - Business rule validation

2. **Add security checks**
   - Permission-based access control
   - Role hierarchy validation

3. **Add audit logging**
   - Log all RBAC changes
   - Create audit trail

### 5.4 Phase 4: Testing and Documentation (Estimated: 1-2 days)

1. **Unit tests**
   - Service layer tests
   - Controller tests

2. **Integration tests**
   - End-to-end API tests
   - Security tests

3. **API documentation**
   - Update API docs
   - Add usage examples

## 6. Database Changes

### 6.1 New Migration (V13__rbac_management.sql)

```sql
-- Add audit table for RBAC changes
CREATE TABLE rbac_audit_log (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL, -- ROLE, PERMISSION, USER_ROLE
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL, -- CREATE, UPDATE, DELETE
    actor_id UUID NOT NULL,
    old_value JSONB,
    new_value JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rbac_audit_entity ON rbac_audit_log(entity_type, entity_id);
CREATE INDEX idx_rbac_audit_actor ON rbac_audit_log(actor_id);
CREATE INDEX idx_rbac_audit_created ON rbac_audit_log(created_at);

-- Add system flag to roles
ALTER TABLE role ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT false;
UPDATE role SET is_system = true WHERE name IN ('ROLE_USER', 'ROLE_ADMIN');

-- Add system flag to permissions
ALTER TABLE permission ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT false;
UPDATE permission SET is_system = true WHERE name IN ('picture:create', 'picture:read', 'admin:review');
```

### 6.2 Entity Updates

Update `Role` and `Permission` entities to include `isSystem` field.

## 7. Error Handling

### 7.1 New Error Codes

```java
// Add to ApiErrorCode enum
ROLE_NOT_FOUND("ROLE_NOT_FOUND", "role not found"),
PERMISSION_NOT_FOUND("PERMISSION_NOT_FOUND", "permission not found"),
ROLE_ALREADY_EXISTS("ROLE_ALREADY_EXISTS", "role already exists"),
PERMISSION_ALREADY_EXISTS("PERMISSION_ALREADY_EXISTS", "permission already exists"),
CANNOT_DELETE_SYSTEM_ROLE("CANNOT_DELETE_SYSTEM_ROLE", "cannot delete system role"),
CANNOT_DELETE_SYSTEM_PERMISSION("CANNOT_DELETE_SYSTEM_PERMISSION", "cannot delete system permission"),
CANNOT_REMOVE_LAST_ADMIN("CANNOT_REMOVE_LAST_ADMIN", "cannot remove last admin role"),
USER_ROLE_ALREADY_ASSIGNED("USER_ROLE_ALREADY_ASSIGNED", "role already assigned to user"),
USER_ROLE_NOT_FOUND("USER_ROLE_NOT_FOUND", "role not assigned to user");
```

### 7.2 Exception Handling

Update `GlobalExceptionHandler` to handle new exceptions.

## 8. Future Enhancements

### 8.1 Role Hierarchy

Support role inheritance where higher-level roles inherit permissions from lower-level roles.

### 8.2 Permission Groups

Organize permissions into logical groups for easier management.

### 8.3 Bulk Operations

Support batch operations for assigning/removing multiple roles.

### 8.4 Role Templates

Pre-defined role templates for common use cases.

### 8.5 Temporary Roles

Support time-limited role assignments.

## 9. Success Criteria

1. **Functional Requirements**
   - All CRUD operations work correctly
   - Permission checks are enforced
   - Data integrity is maintained

2. **Non-Functional Requirements**
   - API response time < 200ms
   - 100% test coverage for new code
   - No security vulnerabilities

3. **User Experience**
   - Clear error messages
   - Consistent API design
   - Comprehensive documentation

## 10. Risks and Mitigations

### 10.1 Technical Risks

1. **Data Consistency** - Use transactions for all RBAC operations
2. **Performance** - Add caching for frequently accessed data
3. **Security** - Regular security audits and penetration testing

### 10.2 Business Risks

1. **Breaking Changes** - Maintain backward compatibility
2. **User Impact** - Gradual rollout with feature flags
3. **Training** - Provide documentation and training materials

## 11. Conclusion

This plan provides a comprehensive roadmap for implementing RBAC management APIs. The phased approach ensures manageable development cycles while maintaining system stability and security. The implementation will significantly enhance the platform's administrative capabilities and security posture.