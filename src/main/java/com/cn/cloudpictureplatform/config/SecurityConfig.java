package com.cn.cloudpictureplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import com.cn.cloudpictureplatform.application.apikey.ApiKeyService;
import com.cn.cloudpictureplatform.infrastructure.security.ApiKeyAuthFilter;
import com.cn.cloudpictureplatform.infrastructure.security.JwtAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            ApiKeyService apiKeyService
    ) {
        var chain = http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/tools/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/pictures/public").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/pictures/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/pictures/recommendations").permitAll()
                        .requestMatchers("/api/files/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAuthority("admin:review")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        if (redisTemplate != null) {
            chain.addFilterBefore(new ApiKeyAuthFilter(apiKeyService, redisTemplate),
                    UsernamePasswordAuthenticationFilter.class);
        }
        return chain.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) {
        return configuration.getAuthenticationManager();
    }
}
