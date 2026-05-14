package com.cn.cloudpictureplatform.application.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.apikey.ApiKey;
import com.cn.cloudpictureplatform.infrastructure.persistence.ApiKeyRepository;

@Service
@Transactional(readOnly = true)
public class ApiKeyService {
    private final ApiKeyRepository apiKeyRepository;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int KEY_BYTES = 32;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Transactional
    public CreatedApiKey createKey(UUID userId, String name, List<String> scopes, int rateLimit, Instant expiresAt) {
        byte[] keyBytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(keyBytes);
        String rawKey = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
        String prefix = rawKey.substring(0, 8);
        String hash = sha256(rawKey);

        ApiKey key = ApiKey.builder()
                .userId(userId).name(name).keyPrefix(prefix).keyHash(hash)
                .scopes(String.join(",", scopes))
                .rateLimit(rateLimit).expiresAt(expiresAt).build();
        apiKeyRepository.save(key);

        return new CreatedApiKey(key.getId(), key.getName(), "cpt_" + rawKey, key.getScopes(),
                key.getRateLimit(), key.getExpiresAt(), key.getCreatedAt());
    }

    public List<ApiKey> listKeys(UUID userId) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void deleteKey(UUID keyId, UUID userId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "api key not found"));
        if (!key.getUserId().equals(userId)) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "not your api key");
        }
        apiKeyRepository.delete(key);
    }

    public Optional<ApiKey> validateKey(String rawKey) {
        if (rawKey == null || !rawKey.startsWith("cpt_")) return Optional.empty();
        String secret = rawKey.substring(4);
        String prefix = secret.length() >= 8 ? secret.substring(0, 8) : secret;
        String hash = sha256(secret);
        ApiKey key = apiKeyRepository.findByKeyPrefixAndKeyHash(prefix, hash).orElse(null);
        if (key == null) return Optional.empty();
        if (key.getExpiresAt() != null && Instant.now().isAfter(key.getExpiresAt())) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    public boolean hasScope(ApiKey key, String requiredScope) {
        if (key.getScopes() == null || key.getScopes().isBlank()) return false;
        for (String scope : key.getScopes().split(",")) {
            if (scope.equals(requiredScope) || scope.equals("*")) return true;
            // wildcard: picture:read matches picture:*
            String[] reqParts = requiredScope.split(":");
            String[] keyParts = scope.split(":");
            if (reqParts.length == 2 && keyParts.length == 2
                    && reqParts[0].equals(keyParts[0]) && keyParts[1].equals("*")) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public record CreatedApiKey(
            UUID id, String name, String rawKey, String scopes,
            int rateLimit, Instant expiresAt, Instant createdAt
    ) {}
}
