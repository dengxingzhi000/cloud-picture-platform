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
        if (userRepository.findByUsername(ADMIN_USERNAME).isPresent()) {
            return;
        }

        AppUser admin = AppUser.builder()
                .username(ADMIN_USERNAME)
                .email("admin@local.dev")
                .passwordHash(passwordEncoder.encode("admin123"))
                .displayName("Admin")
                .status(UserStatus.ACTIVE)
                .build();
        admin = userRepository.save(admin);

        // Assign ROLE_ADMIN
        Role adminRole = roleRepository.findByName(ADMIN_ROLE_NAME).orElse(null);
        if (adminRole != null) {
            UserRole userRole = UserRole.builder()
                    .userId(admin.getId())
                    .roleId(adminRole.getId())
                    .build();
            userRoleRepository.save(userRole);
        }

        // Create personal space
        boolean spaceExists = spaceRepository
                .findFirstByOwnerIdAndType(admin.getId(), SpaceType.PERSONAL)
                .isPresent();
        if (!spaceExists) {
            Space space = Space.builder()
                    .ownerId(admin.getId())
                    .type(SpaceType.PERSONAL)
                    .name("Admin's Space")
                    .quotaBytes(10L * 1024 * 1024 * 1024)
                    .usedBytes(0L)
                    .build();
            spaceRepository.save(space);
        }
    }
}
