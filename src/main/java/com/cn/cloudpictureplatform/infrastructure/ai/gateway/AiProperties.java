package com.cn.cloudpictureplatform.infrastructure.ai.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    private boolean enabled = false;
    private Gateway gateway = new Gateway();
    private Tagging tagging = new Tagging();
    private Moderation moderation = new Moderation();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Gateway getGateway() { return gateway; }
    public void setGateway(Gateway gateway) { this.gateway = gateway; }
    public Tagging getTagging() { return tagging; }
    public void setTagging(Tagging tagging) { this.tagging = tagging; }
    public Moderation getModeration() { return moderation; }
    public void setModeration(Moderation moderation) { this.moderation = moderation; }

    public static class Gateway {
        private String baseUrl = "http://localhost:8000";
        private Timeout timeoutMs = new Timeout();

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public Timeout getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(Timeout timeoutMs) { this.timeoutMs = timeoutMs; }

        public static class Timeout {
            private int embedding = 5000;
            private int tagging = 30000;
            private int moderation = 15000;
            private int chat = 60000;

            public int getEmbedding() { return embedding; }
            public void setEmbedding(int embedding) { this.embedding = embedding; }
            public int getTagging() { return tagging; }
            public void setTagging(int tagging) { this.tagging = tagging; }
            public int getModeration() { return moderation; }
            public void setModeration(int moderation) { this.moderation = moderation; }
            public int getChat() { return chat; }
            public void setChat(int chat) { this.chat = chat; }
        }
    }

    public static class Tagging {
        private boolean enabled = true;
        private double minConfidence = 0.65;
        private int maxTags = 20;
        private String trigger = "upload";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getMinConfidence() { return minConfidence; }
        public void setMinConfidence(double minConfidence) { this.minConfidence = minConfidence; }
        public int getMaxTags() { return maxTags; }
        public void setMaxTags(int maxTags) { this.maxTags = maxTags; }
        public String getTrigger() { return trigger; }
        public void setTrigger(String trigger) { this.trigger = trigger; }
    }

    public static class Moderation {
        private boolean enabled = true;
        private double autoApproveThreshold = 0.92;
        private double autoRejectThreshold = 0.95;
        private boolean escalateToHuman = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getAutoApproveThreshold() { return autoApproveThreshold; }
        public void setAutoApproveThreshold(double autoApproveThreshold) { this.autoApproveThreshold = autoApproveThreshold; }
        public double getAutoRejectThreshold() { return autoRejectThreshold; }
        public void setAutoRejectThreshold(double autoRejectThreshold) { this.autoRejectThreshold = autoRejectThreshold; }
        public boolean isEscalateToHuman() { return escalateToHuman; }
        public void setEscalateToHuman(boolean escalateToHuman) { this.escalateToHuman = escalateToHuman; }
    }
}
