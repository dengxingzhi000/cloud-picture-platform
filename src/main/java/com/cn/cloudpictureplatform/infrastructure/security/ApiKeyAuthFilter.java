package com.cn.cloudpictureplatform.infrastructure.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.cn.cloudpictureplatform.application.apikey.ApiKeyService;
import com.cn.cloudpictureplatform.domain.apikey.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;

public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private final ApiKeyService apiKeyService;
    private final StringRedisTemplate redisTemplate;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, StringRedisTemplate redisTemplate) {
        this.apiKeyService = apiKeyService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<ApiKey> keyOpt = apiKeyService.validateKey(apiKey);
        if (keyOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"invalid api key\"}");
            return;
        }

        ApiKey key = keyOpt.get();

        if (redisTemplate != null && !checkRateLimit(key.getId().toString(), key.getRateLimit())) {
            response.setStatus(429);
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"rate limit exceeded\"}");
            return;
        }

        String requestScope = resolveScope(request);
        if (requestScope != null && !apiKeyService.hasScope(key, requestScope)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"insufficient scope\"}");
            return;
        }

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_API_KEY"));
        var auth = new UsernamePasswordAuthenticationToken(key.getUserId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private boolean checkRateLimit(String keyId, int maxRequests) {
        String bucketKey = "ratelimit:apikey:" + keyId;
        String count = redisTemplate.opsForValue().get(bucketKey);
        if (count == null) {
            redisTemplate.opsForValue().set(bucketKey, "1", java.time.Duration.ofSeconds(60));
            return true;
        }
        int current = Integer.parseInt(count);
        if (current >= maxRequests) return false;
        redisTemplate.opsForValue().increment(bucketKey);
        return true;
    }

    private String resolveScope(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (path.startsWith("/api/pictures") || path.startsWith("/api/albums")) {
            if ("GET".equals(method)) {
                return "picture:read";
            }
            if ("POST".equals(method) || "PATCH".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
                return "picture:write";
            }
            return null;
        }
        if (path.startsWith("/api/teams")) {
            if ("GET".equals(method)) {
                return "team:read";
            }
            return "team:write";
        }
        return null;
    }
}
