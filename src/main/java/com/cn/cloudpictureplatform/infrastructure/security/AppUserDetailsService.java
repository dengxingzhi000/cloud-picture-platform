package com.cn.cloudpictureplatform.infrastructure.security;

import java.util.Optional;

import lombok.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
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
        return new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                enabled,
                user.getRole()
        );
    }
}
