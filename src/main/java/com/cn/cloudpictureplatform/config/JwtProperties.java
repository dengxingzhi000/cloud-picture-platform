package com.cn.cloudpictureplatform.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    @NotBlank
    private String issuer;

    @NotBlank
    private String secret;

    @jakarta.annotation.PostConstruct
    public void validate() {
        String defaultSecret = "dev-only-change-me-in-production-32chars-min";
        if (defaultSecret.equals(secret)) {
            throw new IllegalStateException(
                "JWT_SECRET is using the default value. " +
                "Set the JWT_SECRET environment variable to a cryptographically random string (>= 32 chars). " +
                "Application startup aborted."
            );
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least 32 characters. Current length: " + secret.length()
            );
        }
    }

    @Min(60)
    private long accessTokenTtlSeconds = 7200;
}
