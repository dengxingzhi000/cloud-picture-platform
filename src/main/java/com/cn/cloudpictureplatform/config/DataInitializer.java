package com.cn.cloudpictureplatform.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserRole;
import com.cn.cloudpictureplatform.domain.user.UserStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;

/**
 * 开发用：启动时自动创建 admin 账号（username=admin, password=admin123）。
 * 仅在 admin 账号不存在时执行，生产环境应通过配置禁用或删除此类。
 */
@Component
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true")
public class DataInitializer implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AppUserRepository userRepository,
                           SpaceRepository spaceRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }
        AppUser admin = AppUser.builder()
                .username("admin")
                .email("admin@local.dev")
                .passwordHash(passwordEncoder.encode("admin123"))
                .displayName("Admin")
                .status(UserStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .build();
        admin = userRepository.save(admin);

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
