package com.cn.cloudpictureplatform.infrastructure.security;
import com.cn.cloudpictureplatform.common.security.AppUserPrincipal;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.UserRoleRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.RolePermissionRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.RoleRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public AppUserDetailsService(
            AppUserRepository appUserRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String usernameOrEmail) {
        Optional<AppUser> userOptional = appUserRepository.findByUsername(usernameOrEmail);
        if (userOptional.isEmpty()) {
            userOptional = appUserRepository.findByEmail(usernameOrEmail);
        }
        AppUser user = userOptional.orElseThrow(
                () -> new UsernameNotFoundException("User not found")
        );
        boolean enabled = user.getStatus() == UserStatus.ACTIVE;

        // Load roles
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserId(user.getId());
        Set<String> roles = new HashSet<>();
        for (UUID roleId : roleIds) {
            roleRepository.findById(roleId).ifPresent(r -> roles.add(r.getName()));
        }

        // Load permissions
        Set<String> permissions = new HashSet<>(
                rolePermissionRepository.findPermissionNamesByRoleIds(roleIds)
        );

        return new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                enabled,
                roles,
                permissions
        );
    }
}
