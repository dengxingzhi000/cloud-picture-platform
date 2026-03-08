package com.cn.cloudpictureplatform.infrastructure.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.cn.cloudpictureplatform.domain.user.UserRole;

@Getter
public class AppUserPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final UserRole role;

    public AppUserPrincipal(UUID id, String username, String password, boolean enabled, UserRole role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.role = role == null ? UserRole.USER : role;
    }

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
