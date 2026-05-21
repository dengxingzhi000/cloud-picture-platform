package com.cn.cloudpictureplatform.infrastructure.ai.gateway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
@Getter
@Setter
public class AiProperties {
    private boolean enabled = false;
    private Gateway gateway = new Gateway();
    private Tagging tagging = new Tagging();
    private Moderation moderation = new Moderation();

    @Getter
    @Setter
    public static class Gateway {
        private String baseUrl = "http://localhost:8000";
        private Timeout timeoutMs = new Timeout();
    }

    @Getter
    @Setter
    public static class Timeout {
        private int embedding = 5000;
        private int tagging = 30000;
        private int moderation = 15000;
        private int chat = 60000;
    }

    @Getter
    @Setter
    public static class Tagging {
        private boolean enabled = true;
        private double minConfidence = 0.65;
        private int maxTags = 20;
        private String trigger = "upload";
    }

    @Getter
    @Setter
    public static class Moderation {
        private boolean enabled = true;
        private double autoApproveThreshold = 0.92;
        private double autoRejectThreshold = 0.95;
        private boolean escalateToHuman = true;
    }
}
