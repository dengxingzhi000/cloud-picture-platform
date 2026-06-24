package com.cn.cloudpictureplatform.interfaces.developer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.apikey.ApiKeyService;
import com.cn.cloudpictureplatform.application.apikey.ApiKeyService.CreatedApiKey;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.domain.apikey.ApiKey;
import com.cn.cloudpictureplatform.common.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/v1/developer/keys")
public class DeveloperController {
    private final ApiKeyService apiKeyService;

    public DeveloperController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ApiResponse<CreatedApiKey> createKey(
            @RequestBody CreateKeyRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(apiKeyService.createKey(
                principal.getId(), request.name(), request.scopes(),
                request.rateLimit() != null ? request.rateLimit() : 100,
                request.expiresAt()));
    }

    @GetMapping
    public ApiResponse<List<ApiKey>> listKeys(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(apiKeyService.listKeys(principal.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteKey(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        apiKeyService.deleteKey(id, principal.getId());
        return ApiResponse.ok(null);
    }

    public record CreateKeyRequest(
            String name, List<String> scopes, Integer rateLimit, Instant expiresAt
    ) {}
}
