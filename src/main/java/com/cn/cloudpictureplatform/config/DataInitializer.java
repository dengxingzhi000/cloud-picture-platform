package com.cn.cloudpictureplatform.config;

import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.domain.rbac.Menu;
import com.cn.cloudpictureplatform.domain.rbac.Permission;
import com.cn.cloudpictureplatform.domain.rbac.Role;
import com.cn.cloudpictureplatform.domain.rbac.RoleMenu;
import com.cn.cloudpictureplatform.domain.rbac.RolePermission;
import com.cn.cloudpictureplatform.domain.rbac.UserRole;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.MenuRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PermissionRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.RoleMenuRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.RolePermissionRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.RoleRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.UserRoleRepository;
import org.springframework.jdbc.core.JdbcTemplate;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true")
public class DataInitializer implements ApplicationRunner {
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_ROLE_NAME = "ROLE_ADMIN";

    private final AppUserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(AppUserRepository userRepository,
                           SpaceRepository spaceRepository,
                           PasswordEncoder passwordEncoder,
                           RoleRepository roleRepository,
                           UserRoleRepository userRoleRepository,
                           PermissionRepository permissionRepository,
                           MenuRepository menuRepository,
                           RolePermissionRepository rolePermissionRepository,
                           RoleMenuRepository roleMenuRepository,
                           JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.permissionRepository = permissionRepository;
        this.menuRepository = menuRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleMenuRepository = roleMenuRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Ensure roles exist
        Role adminRole = ensureRole("ROLE_ADMIN", "System administrator", true);
        Role moderatorRole = ensureRole("ROLE_MODERATOR", "Content moderator", true);
        Role userRole = ensureRole("ROLE_USER", "Default user role", true);

        // Initialize permissions
        initPermissions();

        // Initialize role-permission mappings
        initRolePermissions(adminRole, moderatorRole, userRole);

        // Initialize menus (will skip if already exists)
        initMenus();

        // Initialize role-menu mappings (will skip if already exists)
        initRoleMenus(adminRole, moderatorRole);

        // Create test accounts
        ensureUser("admin", "admin@local.dev", "admin123", "Admin", adminRole);
        ensureUser("moderator", "moderator@local.dev", "mod123", "Moderator", moderatorRole);
        ensureUser("testuser", "user@local.dev", "user123", "Test User", userRole);
        ensureUser("alice", "alice@local.dev", "alice123", "Alice Wang", userRole);
        ensureUser("bob", "bob@local.dev", "bob123", "Bob Li", moderatorRole);
    }

    private Role ensureRole(String name, String description, boolean isSystem) {
        return roleRepository.findByName(name).orElseGet(() ->
                roleRepository.save(Role.builder()
                        .name(name)
                        .description(description)
                        .isSystem(isSystem)
                        .build())
        );
    }

    private void ensureUser(String username, String email, String password, String displayName, Role role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        AppUser user = AppUser.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .displayName(displayName)
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);

        UserRole ur = UserRole.builder()
                .userId(user.getId())
                .roleId(role.getId())
                .build();
        userRoleRepository.save(ur);

        // Create personal space
        boolean spaceExists = spaceRepository
                .findFirstByOwnerIdAndType(user.getId(), SpaceType.PERSONAL)
                .isPresent();
        if (!spaceExists) {
            Space space = Space.builder()
                    .ownerId(user.getId())
                    .type(SpaceType.PERSONAL)
                    .name(displayName + "'s Space")
                    .quotaBytes(10L * 1024 * 1024 * 1024)
                    .usedBytes(0L)
                    .build();
            spaceRepository.save(space);
        }
    }

    private void initPermissions() {
        // Picture permissions
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000001"), "picture:create", "Upload pictures", "picture", "create", true);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000002"), "picture:read", "View picture details", "picture", "read", true);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000003"), "picture:update", "Edit picture metadata", "picture", "update", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000004"), "picture:delete", "Delete pictures", "picture", "delete", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000005"), "picture:tag", "Manage tags on pictures", "picture", "tag", false);

        // Tag permissions
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000011"), "tag:create", "Create tags in catalog", "tag", "create", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000012"), "tag:read", "View tags", "tag", "read", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000013"), "tag:update", "Update tags", "tag", "update", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000014"), "tag:delete", "Delete tags", "tag", "delete", false);

        // Team permissions
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000021"), "team:create", "Create teams", "team", "create", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000022"), "team:read", "View teams and members", "team", "read", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000023"), "team:update", "Update team settings", "team", "update", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000024"), "team:delete", "Delete teams", "team", "delete", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000025"), "team:invite", "Invite members to teams", "team", "invite", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000026"), "team:manage-roles", "Manage member roles", "team", "manage-roles", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000027"), "team:remove-member", "Remove team members", "team", "remove-member", false);

        // Admin permissions
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000031"), "admin:review", "Moderate picture submissions", "admin", "review", true);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000032"), "admin:search", "Manage search index", "admin", "search", false);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000033"), "admin:user", "Manage users", "admin", "user", false);

        // RBAC management permissions
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000041"), "admin:role", "Manage roles", "admin", "role", true);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000042"), "admin:permission", "Manage permissions", "admin", "permission", true);
        ensurePermission(UUID.fromString("b0000000-0000-0000-0000-000000000043"), "admin:user-role", "Manage user role assignments", "admin", "user-role", true);
    }

    private void ensurePermission(UUID id, String name, String description, String resource, String action, boolean isSystem) {
        if (permissionRepository.existsByName(name)) {
            return;
        }
        permissionRepository.save(Permission.builder()
                .name(name)
                .description(description)
                .resource(resource)
                .action(action)
                .isSystem(isSystem)
                .build());
    }

    private void initRolePermissions(Role adminRole, Role moderatorRole, Role userRole) {
        // ROLE_USER permissions
        String[] userPermissionNames = {
            "picture:create", "picture:read", "picture:update", "picture:delete", "picture:tag",
            "tag:create", "tag:read", "tag:update", "tag:delete",
            "team:create", "team:read", "team:invite"
        };
        for (String permName : userPermissionNames) {
            permissionRepository.findByName(permName).ifPresent(perm ->
                ensureRolePermission(userRole.getId(), perm.getId())
            );
        }

        // ROLE_MODERATOR permissions (USER + admin:review)
        for (String permName : userPermissionNames) {
            permissionRepository.findByName(permName).ifPresent(perm ->
                ensureRolePermission(moderatorRole.getId(), perm.getId())
            );
        }
        permissionRepository.findByName("admin:review").ifPresent(perm ->
            ensureRolePermission(moderatorRole.getId(), perm.getId())
        );

        // ROLE_ADMIN: all permissions
        permissionRepository.findAll().forEach(perm ->
            ensureRolePermission(adminRole.getId(), perm.getId())
        );
    }

    private void ensureRolePermission(UUID roleId, UUID permissionId) {
        if (rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            return;
        }
        try {
            jdbcTemplate.update(
                "INSERT INTO role_permission (role_id, permission_id, created_at) VALUES (?, ?, NOW())",
                roleId, permissionId
            );
        } catch (Exception e) {
            System.err.println("Warning: Failed to create role_permission: " + e.getMessage());
        }
    }

    private void initMenus() {
        // Admin root menus
        ensureMenu(UUID.fromString("c0000000-0000-0000-0000-000000000001"), "Dashboard", "dashboard", null, "/admin", "layout-dashboard", 1, true);
        ensureMenu(UUID.fromString("c0000000-0000-0000-0000-000000000002"), "Content Review", "content_review", null, "/admin/reviews", "shield-check", 2, true);
        ensureMenu(UUID.fromString("c0000000-0000-0000-0000-000000000003"), "User Management", "user_management", null, "/admin/users", "users", 3, true);
        ensureMenu(UUID.fromString("c0000000-0000-0000-0000-000000000004"), "Role Management", "role_management", null, "/admin/roles", "user-cog", 4, true);
        ensureMenu(UUID.fromString("c0000000-0000-0000-0000-000000000005"), "Permissions", "permission_mgmt", null, "/admin/permissions", "key", 5, true);
        ensureMenu(UUID.fromString("c0000000-0000-0000-0000-000000000006"), "Search Index", "search_index", null, "/admin/search-index", "search", 6, true);
    }

    private void ensureMenu(UUID id, String name, String code, UUID parentId, String path, String icon, int sortOrder, boolean isVisible) {
        // Check if menu already exists by ID or code
        if (menuRepository.existsById(id)) {
            return;
        }
        if (menuRepository.findByCode(code).isPresent()) {
            return;
        }
        // Menu doesn't exist, create it
        try {
            // Use native SQL with ON CONFLICT to safely handle duplicate IDs
            jdbcTemplate.update(
                "INSERT INTO menu (id, name, code, parent_id, path, icon, sort_order, is_visible, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                id, name, code,
                parentId,
                path, icon, sortOrder, isVisible
            );
        } catch (Exception e) {
            // If creation fails (e.g., concurrent insert), log and continue
            System.err.println("Warning: Failed to create menu " + code + ": " + e.getMessage());
        }
    }

    private void initRoleMenus(Role adminRole, Role moderatorRole) {
        // ROLE_MODERATOR: Dashboard + Content Review
        ensureRoleMenu(moderatorRole.getId(), UUID.fromString("c0000000-0000-0000-0000-000000000001"));
        ensureRoleMenu(moderatorRole.getId(), UUID.fromString("c0000000-0000-0000-0000-000000000002"));

        // ROLE_ADMIN: All menus
        ensureRoleMenu(adminRole.getId(), UUID.fromString("c0000000-0000-0000-0000-000000000001"));
        ensureRoleMenu(adminRole.getId(), UUID.fromString("c0000000-0000-0000-0000-000000000002"));
        ensureRoleMenu(adminRole.getId(), UUID.fromString("c0000000-0000-0000-0000-000000000003"));
        ensureRoleMenu(adminRole.getId(), UUID.fromString("c0000000-0000-0000-0000-000000000004"));
        ensureRoleMenu(adminRole.getId(), UUID.fromString("c0000000-0000-0000-0000-000000000005"));
        ensureRoleMenu(adminRole.getId(), UUID.fromString("c0000000-0000-0000-0000-000000000006"));
    }

    private void ensureRoleMenu(UUID roleId, UUID menuId) {
        try {
            if (roleMenuRepository.existsByRoleIdAndMenuId(roleId, menuId)) {
                return;
            }
            jdbcTemplate.update(
                "INSERT INTO role_menu (role_id, menu_id, created_at) VALUES (?, ?, NOW())",
                roleId, menuId
            );
        } catch (Exception e) {
            System.err.println("Warning: Failed to create role_menu: " + e.getMessage());
        }
    }
}
