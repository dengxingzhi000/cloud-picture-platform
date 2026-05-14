package com.cn.cloudpictureplatform.application.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.webhook.WebhookDelivery;
import com.cn.cloudpictureplatform.domain.webhook.WebhookEndpoint;
import com.cn.cloudpictureplatform.infrastructure.persistence.WebhookDeliveryRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.WebhookEndpointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
public class WebhookService {
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebhookService(
            WebhookEndpointRepository webhookEndpointRepository,
            WebhookDeliveryRepository webhookDeliveryRepository,
            ObjectMapper objectMapper
    ) {
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Transactional
    public WebhookEndpoint registerEndpoint(UUID ownerId, String ownerType, String url, String secret, List<String> events) {
        WebhookEndpoint ep = WebhookEndpoint.builder()
                .ownerId(ownerId).ownerType(ownerType).url(url).secret(secret)
                .events(listToJson(events)).active(true).build();
        return webhookEndpointRepository.save(ep);
    }

    public List<WebhookEndpoint> listEndpoints(UUID ownerId, String ownerType) {
        return webhookEndpointRepository.findByOwnerIdAndOwnerTypeAndActiveTrue(ownerId, ownerType);
    }

    @Transactional
    public void deleteEndpoint(UUID id) {
        webhookEndpointRepository.deleteById(id);
    }

    public PageResponse<WebhookDelivery> getDeliveries(UUID webhookId, int page, int size) {
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), 50);
        var pageable = PageRequest.of(pageIndex, pageSize);
        var result = webhookDeliveryRepository.findByWebhookIdOrderByCreatedAtDesc(webhookId, pageable);
        return new PageResponse<>(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional
    public void deliver(UUID webhookId, String eventType, Object payload) {
        WebhookEndpoint ep = webhookEndpointRepository.findById(webhookId).orElse(null);
        if (ep == null || !ep.isActive()) return;

        String body;
        try { body = objectMapper.writeValueAsString(payload); } catch (Exception e) { return; }

        long start = System.currentTimeMillis();
        try {
            String signature = computeHmac(ep.getSecret(), body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ep.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Signature", signature)
                    .header("X-Webhook-Event", eventType)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int duration = (int) (System.currentTimeMillis() - start);
            boolean success = resp.statusCode() >= 200 && resp.statusCode() < 300;
            webhookDeliveryRepository.save(WebhookDelivery.builder()
                    .webhookId(webhookId).eventType(eventType)
                    .requestUrl(ep.getUrl()).requestBody(body)
                    .responseStatus(resp.statusCode())
                    .responseBody(resp.body())
                    .success(success).durationMs(duration).build());
            if (!success) {
                log.warn("Webhook delivery failed: status={}, webhookId={}", resp.statusCode(), webhookId);
            }
        } catch (Exception ex) {
            int duration = (int) (System.currentTimeMillis() - start);
            webhookDeliveryRepository.save(WebhookDelivery.builder()
                    .webhookId(webhookId).eventType(eventType)
                    .requestUrl(ep.getUrl()).requestBody(body)
                    .success(false).durationMs(duration).build());
            log.warn("Webhook delivery error: webhookId={}, error={}", webhookId, ex.getMessage());
        }
    }

    private static String computeHmac(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return "sha256=" + sb;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return "";
        }
    }

    private static String listToJson(List<String> events) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(events);
        } catch (Exception e) {
            return "[]";
        }
    }
}
