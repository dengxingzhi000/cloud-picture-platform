package com.cn.cloudpictureplatform.interfaces.webhook;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.webhook.WebhookService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.webhook.WebhookDelivery;
import com.cn.cloudpictureplatform.domain.webhook.WebhookEndpoint;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ApiResponse<WebhookEndpoint> register(
            @RequestBody WebhookCreateRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(webhookService.registerEndpoint(
                principal.getId(), "USER", request.url(), request.secret(), request.events()));
    }

    @GetMapping
    public ApiResponse<List<WebhookEndpoint>> list(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(webhookService.listEndpoints(principal.getId(), "USER"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") UUID id) {
        webhookService.deleteEndpoint(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Void> test(
            @PathVariable("id") UUID id
    ) {
        webhookService.deliver(id, "test.ping", Map.of("message", "ping"));
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/deliveries")
    public ApiResponse<PageResponse<WebhookDelivery>> getDeliveries(
            @PathVariable("id") UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(webhookService.getDeliveries(id, page, size));
    }

    public record WebhookCreateRequest(
            String url, String secret, List<String> events
    ) {}
}
