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

    @Min(60)
    private long accessTokenTtlSeconds = 7200;
}
