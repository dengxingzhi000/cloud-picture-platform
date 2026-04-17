package com.cn.cloudpictureplatform.infrastructure.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;
    private final AppUserDetailsService appUserDetailsService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, AppUserDetailsService appUserDetailsService) {
        this.jwtTokenService = jwtTokenService;
        this.appUserDetailsService = appUserDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtTokenService.isTokenValid(token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                String username = jwtTokenService.extractUsername(token);
                try {
                    var userDetails = appUserDetailsService.loadUserByUsername(username);
                    if (userDetails.isEnabled()) {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (UsernameNotFoundException ignored) {
                    // Token is valid but user no longer exists (e.g. in-memory DB restart).
                    // Proceed as unauthenticated — downstream security rules will reject if needed.
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
