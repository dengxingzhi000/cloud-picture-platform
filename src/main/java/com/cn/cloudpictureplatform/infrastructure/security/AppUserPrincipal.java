package com.cn.cloudpictureplatform.infrastructure.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class AppUserPrincipal implements UserDetails {
    private final UUID id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Set<String> roles;
    private final Set<String> permissions;

    public AppUserPrincipal(UUID id, String username, String password, boolean enabled,
                            Set<String> roles, Set<String> permissions) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.roles = roles != null ? roles : Set.of();
        this.permissions = permissions != null ? permissions : Set.of();
    }

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Permissions are the authorities used by Spring Security hasAuthority()
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasRole(String roleName) {
        return roles.contains(roleName);
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
