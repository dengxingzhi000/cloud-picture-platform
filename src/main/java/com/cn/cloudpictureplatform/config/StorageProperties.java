package com.cn.cloudpictureplatform.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    @NotBlank
    private String provider = "local";

    private Local local = new Local();

    @Getter
    @Setter
    public static class Local {
        @NotBlank
        private String root = "data/uploads";
    }
}
