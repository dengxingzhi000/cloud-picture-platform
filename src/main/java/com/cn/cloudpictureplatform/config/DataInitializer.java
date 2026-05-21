package com.cn.cloudpictureplatform.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.domain.rbac.Role;
import com.cn.cloudpictureplatform.domain.rbac.UserRole;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.RoleRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.UserRoleRepository;

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

    public DataInitializer(AppUserRepository userRepository,
                           SpaceRepository spaceRepository,
                           PasswordEncoder passwordEncoder,
                           RoleRepository roleRepository,
                           UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Ensure roles exist
        Role adminRole = ensureRole("ROLE_ADMIN", "System administrator", true);
        Role moderatorRole = ensureRole("ROLE_MODERATOR", "Content moderator", true);
        Role userRole = ensureRole("ROLE_USER", "Default user role", true);

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
}
