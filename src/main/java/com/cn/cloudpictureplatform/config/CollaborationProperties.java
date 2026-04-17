package com.cn.cloudpictureplatform.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Collaboration room bootstrap settings for an external Yjs-compatible realtime service.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.collaboration")
public class CollaborationProperties {

    @NotBlank
    private String provider = "yjs-websocket";

    @NotBlank
    private String roomPrefix = "picture";

    @NotBlank
    private String publicUrl = "ws://localhost:1234";

    @Min(60)
    private long roomTokenTtlSeconds = 900;

    private boolean awarenessEnabled = true;

    private boolean indexedDbRecommended = true;
}
