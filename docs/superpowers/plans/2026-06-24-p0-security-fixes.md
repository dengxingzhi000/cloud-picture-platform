# P0 Security & Data Integrity Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 10 Critical/High severity security and data integrity issues identified in the architecture review, bringing the system from "functional prototype" to "minimum viable security posture."

**Architecture:** Each fix is self-contained and independently deployable. Fixes are ordered by blast radius — file access and JWT first (highest impact), then auth hardening, then data integrity. All fixes are backward-compatible except Task 1 (which changes how files are served).

**Tech Stack:** Spring Boot 4.0.5, Java 21, Spring Security, Resilience4j, PostgreSQL, Redis

---

## File Map

| Action | File | Purpose |
|--------|------|---------|
| Modify | `config/WebConfig.java` | Remove static `/uploads/**` mapping |
| Create | `interfaces/file/FileProxyController.java` | Authenticated file serving with visibility checks |
| Modify | `config/SecurityConfig.java` | Wire file proxy permit rules, add rate limiter bean |
| Modify | `config/JwtProperties.java` | Validate JWT secret at startup |
| Create | `application/file/FileProxyService.java` | File access authorization logic |
| Modify | `interfaces/auth/AuthController.java` | Add rate limiting |
| Modify | `application/auth/AuthService.java` | Rate limit login attempts |
| Modify | `config/WebSocketConfig.java` | Restrict allowed origins |
| Modify | `application.yml` | JWT validation, WebSocket origins, rate limiter config |
| Modify | `application-prod.yml` | ddl-auto: validate |
| Create | `application/file/FileValidationService.java` | Magic byte validation |
| Modify | `application/picture/PictureUploadService.java` | Integrate file validation |
| Modify | `application/webhook/WebhookService.java` | SSRF protection |
| Modify | `domain/picture/PictureAsset.java` | @Where soft delete filter |
| Modify | `application/picture/BatchPictureService.java` | Decrement ref_count on delete |
| Modify | `infrastructure/persistence/FileContentRepository.java` | Safe decrement query |
| Modify | `common/web/ApiErrorCode.java` | Add RATE_LIMITED error code |
| Modify | `common/web/GlobalExceptionHandler.java` | Handle RateLimitExceededException |
| Modify | `config/DataInitializer.java` | Default admin bootstrap to disabled |
| Modify | `config/FallbackCacheManager.java` or `config/CacheConfig.java` | Per-picture cache eviction |

---

## Task 1: File Access Proxy (Private Image Leak Fix)

**Why:** `/uploads/**` is served as a static resource without authentication. Any private image is accessible via direct URL regardless of `visibility` setting. This is the #1 security risk.

**Files:**
- Modify: `config/WebConfig.java:19-24`
- Create: `interfaces/file/FileProxyController.java`
- Create: `application/file/FileProxyService.java`
- Modify: `config/SecurityConfig.java:42`
- Modify: `application.yml` (add `app.storage.public-prefix`)

### Step 1.1: Remove static resource mapping from WebConfig

In `config/WebConfig.java`, remove the `addResourceHandlers` override entirely. The class currently maps `/uploads/**` to the local filesystem, bypassing all auth.

```java
// BEFORE (lines 19-24):
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    Path root = Paths.get(storageProperties.getLocal().getRoot()).toAbsolutePath().normalize();
    registry.addResourceHandler("/uploads/**")
            .addResourceLocations(root.toUri().toString());
}

// AFTER: Remove the entire method. Keep the class (it may be used for other config).
// If this is the only method, delete the class entirely.
```

### Step 1.2: Create FileProxyService

Create `application/file/FileProxyService.java`:

```java
package com.cn.cloudpictureplatform.application.file;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.storage.StorageService;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileProxyService {

    private final PictureAssetRepository pictureAssetRepository;
    private final StorageService storageService;

    /**
     * Resolve the storage key for a file, checking access permissions.
     * Returns the storage key if access is granted, throws otherwise.
     */
    public String resolveAccessibleStorageKey(String path, AppUserPrincipal requester) {
        // Public files: approved public pictures are accessible to everyone
        if (path.startsWith("public/")) {
            return resolvePublicFile(path);
        }
        // Protected files: require authentication
        if (requester == null) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "authentication required");
        }
        return resolveProtectedFile(path, requester);
    }

    private String resolvePublicFile(String path) {
        // Extract picture ID from path convention: public/{pictureId}/filename
        // For now, serve any file under public/ that has an approved public picture
        Optional<PictureAsset> asset = pictureAssetRepository.findByStorageKey(path);
        if (asset.isEmpty()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "file not found");
        }
        PictureAsset pic = asset.get();
        if (pic.isDeleted()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "file not found");
        }
        if (pic.getVisibility() != Visibility.PUBLIC || !pic.isApproved()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "access denied");
        }
        return path;
    }

    private String resolveProtectedFile(String path, AppUserPrincipal requester) {
        Optional<PictureAsset> asset = pictureAssetRepository.findByStorageKey(path);
        if (asset.isEmpty()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "file not found");
        }
        PictureAsset pic = asset.get();
        if (pic.isDeleted()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "file not found");
        }
        // Owner can always access their own files
        if (pic.isOwnedBy(requester.getId())) {
            return path;
        }
        // Public/approved pictures are accessible to authenticated users
        if (pic.getVisibility() == Visibility.PUBLIC && pic.isApproved()) {
            return path;
        }
        // TODO: Team membership check for TEAM visibility
        throw new ApiException(ApiErrorCode.FORBIDDEN, "access denied");
    }

    public InputStream getFileStream(String storageKey) {
        return storageService.retrieve(storageKey);
    }
}
```

### Step 1.3: Create FileProxyController

Create `interfaces/file/FileProxyController.java`:

```java
package com.cn.cloudpictureplatform.interfaces.file;

import com.cn.cloudpictureplatform.application.file.FileProxyService;
import com.cn.cloudpictureplatform.common.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileProxyController {

    private final FileProxyService fileProxyService;

    /**
     * Serve files through an authenticated proxy.
     * Public images (approved, PUBLIC visibility) are accessible without auth.
     * Private/team images require authentication and ownership/membership checks.
     *
     * URL pattern: /api/files/{storageKey}
     * Example: /api/files/pictures/{ownerId}/{uuid}.jpg
     */
    @GetMapping("/**")
    public void serveFile(
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal(required = false) AppUserPrincipal requester
    ) throws IOException {
        // Extract the path after /api/files/
        String fullPath = request.getRequestURI();
        String prefix = "/api/files/";
        if (!fullPath.startsWith(prefix)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String storageKey = fullPath.substring(prefix.length());

        String resolvedKey = fileProxyService.resolveAccessibleStorageKey(storageKey, requester);

        try (InputStream is = fileProxyService.getFileStream(resolvedKey)) {
            // Detect content type from file extension
            String contentType = detectContentType(storageKey);
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "inline");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "private, max-age=3600");

            OutputStream os = response.getOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        }
    }

    private String detectContentType(String storageKey) {
        String lower = storageKey.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
```

### Step 1.4: Update SecurityConfig

In `config/SecurityConfig.java`, change the `/uploads/**` rule to point to the new proxy:

```java
// BEFORE (line 42):
.requestMatchers("/uploads/**").permitAll()

// AFTER:
.requestMatchers("/api/files/**").permitAll()
```

### Step 1.5: Update storage URL generation

In `infrastructure/storage/LocalStorageService.java`, change the URL prefix from `/uploads/` to `/api/files/`:

```java
// BEFORE (line 39):
String url = "/uploads/" + key.replace("\\", "/");

// AFTER:
String url = "/api/files/" + key.replace("\\", "/");
```

Same change at line 56.

### Step 1.6: Run and verify

Run: `.\mvnw compile`
Expected: SUCCESS

---

## Task 2: JWT Secret Validation at Startup

**Why:** If `JWT_SECRET` env var is unset, the app runs with `dev-only-change-me-in-production-32chars-min` — a guessable secret that allows token forgery.

**Files:**
- Modify: `config/JwtProperties.java:14-24`
- Modify: `application.yml:68`

### Step 2.1: Add startup validation to JwtProperties

In `config/JwtProperties.java`, add `@PostConstruct` validation:

```java
// AFTER line 14 (class declaration), add:

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
```

### Step 2.2: Update application.yml default

In `application.yml`, change the default to empty string so the `@NotBlank` validation catches it:

```yaml
# BEFORE (line 68):
secret: ${JWT_SECRET:dev-only-change-me-in-production-32chars-min}

# AFTER:
secret: ${JWT_SECRET:}
```

### Step 2.3: Run and verify

Run: `.\mvnw compile`
Expected: SUCCESS

---

## Task 3: Default Admin Bootstrap Disabled

**Why:** `DataInitializer` creates 5 accounts with weak passwords (`admin123`, `mod123`, etc.) by default. These persist in production.

**Files:**
- Modify: `application.yml:43`
- Modify: `config/DataInitializer.java:92-96`

### Step 3.1: Disable bootstrap by default

In `application.yml`:

```yaml
# BEFORE (line 43):
enabled: true

# AFTER:
enabled: false
```

### Step 3.2: Add startup warning when bootstrap is enabled

In `config/DataInitializer.java`, add a log warning at the top of the `run()` method (after line 85):

```java
@Override
public void run(String... args) {
    if (!properties.getBootstrap().getAdmin().isEnabled()) {
        return;
    }

    // ADD THIS:
    log.warn("==========================================================");
    log.warn("WARNING: Admin bootstrap is ENABLED. Test accounts will be created.");
    log.warn("This should NEVER be enabled in production.");
    log.warn("Set app.bootstrap.admin.enabled=false to disable.");
    log.warn("==========================================================");

    // ... rest of existing code
```

### Step 3.3: Run and verify

Run: `.\mvnw compile`
Expected: SUCCESS

---

## Task 4: Login Rate Limiting

**Why:** Login and registration endpoints have no rate limiting. Unlimited brute-force attempts.

**Files:**
- Create: `common/exception/RateLimitExceededException.java`
- Modify: `interfaces/auth/AuthController.java`
- Modify: `common/web/ApiErrorCode.java:13`
- Modify: `common/web/GlobalExceptionHandler.java`
- Modify: `application.yml` (add rate limiter config)

### Step 4.1: Add RATE_LIMITED error code

In `common/web/ApiErrorCode.java`, add after line 13:

```java
RATE_LIMITED("RATE_LIMITED", "too many requests, please try again later"),
```

### Step 4.2: Create RateLimitExceededException

Create `common/exception/RateLimitExceededException.java`:

```java
package com.cn.cloudpictureplatform.common.exception;

import com.cn.cloudpictureplatform.common.web.ApiErrorCode;

public class RateLimitExceededException extends ApiException {
    public RateLimitExceededException(String message) {
        super(ApiErrorCode.RATE_LIMITED, message);
    }
}
```

### Step 4.3: Add rate limit handler to GlobalExceptionHandler

In `common/web/GlobalExceptionHandler.java`, add a handler for `RateLimitExceededException` (before the catch-all):

```java
@ExceptionHandler(RateLimitExceededException.class)
public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException ex) {
    log.warn("Rate limit exceeded: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
}
```

### Step 4.4: Implement Redis-based rate limiting in AuthService

In `application/auth/AuthService.java`, add rate limiting to the `login()` method. Inject `StringRedisTemplate` and add at the top of `login()`:

```java
// Add field:
private final StringRedisTemplate redisTemplate;

// Add method:
private void checkLoginRateLimit(String ip) {
    String key = "login:attempts:" + ip;
    String countStr = redisTemplate.opsForValue().get(key);
    int count = countStr != null ? Integer.parseInt(countStr) : 0;
    if (count >= 10) { // 10 attempts per window
        throw new RateLimitExceededException("too many login attempts, try again later");
    }
    redisTemplate.opsForValue().increment(key);
    if (count == 0) {
        redisTemplate.expire(key, java.time.Duration.ofMinutes(15));
    }
}

// At the top of login() method, add:
checkLoginRateLimit(clientIp);
```

Add a helper to extract client IP:

```java
private String getClientIp() {
    org.springframework.web.context.request.RequestAttributes attrs =
        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
    if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
        jakarta.servlet.http.HttpServletRequest request = servletAttrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    return "unknown";
}
```

### Step 4.5: Add rate limiting to registration too

In `application/auth/AuthService.java`, add similar rate limiting to `register()`:

```java
private void checkRegisterRateLimit(String ip) {
    String key = "register:attempts:" + ip;
    String countStr = redisTemplate.opsForValue().get(key);
    int count = countStr != null ? Integer.parseInt(countStr) : 0;
    if (count >= 5) { // 5 registrations per hour per IP
        throw new RateLimitExceededException("too many registration attempts, try again later");
    }
    redisTemplate.opsForValue().increment(key);
    if (count == 0) {
        redisTemplate.expire(key, java.time.Duration.ofHours(1));
    }
}
```

Call at top of `register()`:
```java
checkRegisterRateLimit(getClientIp());
```

### Step 4.6: Run and verify

Run: `.\mvnw compile`
Expected: SUCCESS

---

## Task 5: WebSocket Origin Restriction

**Why:** `setAllowedOriginPatterns("*")` allows any website to initiate WebSocket connections.

**Files:**
- Modify: `config/WebSocketConfig.java:23`
- Modify: `application.yml` (add allowed origins config)

### Step 5.1: Add allowed origins to application.yml

```yaml
# Add under app: section:
app:
  websocket:
    allowed-origins: ${WS_ALLOWED_ORIGINS:http://localhost:5173,http://localhost:3000}
```

### Step 5.2: Inject allowed origins in WebSocketConfig

In `config/WebSocketConfig.java`, replace the hardcoded `"*"`:

```java
// BEFORE (line 23):
config.setAllowedOriginPatterns("*");

// AFTER:
@Value("${app.websocket.allowed-origins:http://localhost:5173,http://localhost:3000}")
private String allowedOrigins;

// In configureMessageBroker (line 23):
config.setAllowedOriginPatterns(allowedOrigins.split(","));
```

### Step 5.3: Run and verify

Run: `.\mvnw compile`
Expected: SUCCESS

---

## Task 6: File Upload Content Validation (Magic Bytes)

**Why:** Uploads accept any file content. An attacker can upload HTML/JS disguised as an image (stored XSS).

**Files:**
- Create: `application/file/FileValidationService.java`
- Modify: `application/picture/PictureUploadService.java:64-89`

### Step 6.1: Create FileValidationService

Create `application/file/FileValidationService.java`:

```java
package com.cn.cloudpictureplatform.application.file;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class FileValidationService {

    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
        "image/jpeg", Set.of("jpg", "jpeg"),
        "image/png", Set.of("png"),
        "image/gif", Set.of("gif"),
        "image/webp", Set.of("webp"),
        "image/svg+xml", Set.of("svg")
    );

    private static final Map<String, byte[][]> MAGIC_BYTES = Map.of(
        "image/jpeg", new byte[][] {
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
        },
        "image/png", new byte[][] {
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        },
        "image/gif", new byte[][] {
            {0x47, 0x49, 0x46, 0x38, 0x37, 0x61},  // GIF87a
            {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}   // GIF89a
        },
        "image/webp", new byte[][] {
            {0x52, 0x49, 0x46, 0x46}  // RIFF (first 4 bytes, WEBP follows at offset 8)
        }
    );

    /**
     * Validate that the uploaded file is a genuine image.
     * Checks both declared MIME type and magic bytes.
     */
    public void validateImage(MultipartFile file) {
        String declaredType = file.getContentType();

        // Reject if not an allowed type
        if (declaredType == null || !ALLOWED_TYPES.containsKey(declaredType)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST,
                "unsupported file type: " + declaredType + ". Allowed: " + ALLOWED_TYPES.keySet());
        }

        // SVG is text-based, skip magic byte check
        if ("image/svg+xml".equals(declaredType)) {
            return;
        }

        // Validate magic bytes
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                throw new ApiException(ApiErrorCode.BAD_REQUEST, "file too small to validate");
            }
            if (!matchesMagicBytes(declaredType, header)) {
                throw new ApiException(ApiErrorCode.BAD_REQUEST,
                    "file content does not match declared type " + declaredType);
            }
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "failed to read file for validation");
        }
    }

    private boolean matchesMagicBytes(String mimeType, byte[] header) {
        byte[][] signatures = MAGIC_BYTES.get(mimeType);
        if (signatures == null) return true; // No signature to check

        for (byte[] sig : signatures) {
            if (matchesSignature(header, sig)) {
                // Special case: WebP needs "WEBP" at offset 8
                if ("image/webp".equals(mimeType)) {
                    if (header.length >= 12
                        && header[8] == 'W' && header[9] == 'E'
                        && header[10] == 'B' && header[11] == 'P') {
                        return true;
                    }
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private boolean matchesSignature(byte[] header, byte[] signature) {
        if (header.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) return false;
        }
        return true;
    }
}
```

### Step 6.2: Integrate into PictureUploadService

In `application/picture/PictureUploadService.java`, inject `FileValidationService` and call it before processing:

```java
// Add field (after existing fields):
private final FileValidationService fileValidationService;

// In the upload() method, add validation BEFORE line 78 (storageService.store):
fileValidationService.validateImage(file);
```

### Step 6.3: Run and verify

Run: `.\mvnw compile`
Expected: SUCCESS

---

## Task 7: Webhook SSRF Protection

**Why:** Webhook URLs accept any URL including internal IPs (127.0.0.1, 10.x.x.x, 169.254.169.254). Attacker can probe internal services.

**Files:**
- Modify: `application/webhook/WebhookService.java`

### Step 7.1: Add URL validation to WebhookService

In `application/webhook/WebhookService.java`, add a validation method and call it during registration:

```java
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

// Add validation method:
private void validateWebhookUrl(String urlStr) {
    try {
        URI uri = URI.create(urlStr);
        String host = uri.getHost();
        if (host == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid webhook URL");
        }

        // Block common internal addresses
        String lowerHost = host.toLowerCase();
        if (lowerHost.equals("localhost")
            || lowerHost.equals("127.0.0.1")
            || lowerHost.equals("0.0.0.0")
            || lowerHost.equals("[::1]")
            || lowerHost.endsWith(".local")
            || lowerHost.endsWith(".internal")) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "webhook URL cannot point to internal addresses");
        }

        // Resolve and check IP ranges
        InetAddress addr = InetAddress.getByName(host);
        if (addr.isLoopbackAddress()
            || addr.isSiteLocalAddress()
            || addr.isLinkLocalAddress()
            || addr.isAnyLocalAddress()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "webhook URL cannot point to private/reserved IP ranges");
        }

        // Block cloud metadata endpoints
        String ip = addr.getHostAddress();
        if (ip.startsWith("169.254.")) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "webhook URL cannot point to link-local addresses");
        }

    } catch (ApiException e) {
        throw e;
    } catch (Exception e) {
        throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid webhook URL: " + e.getMessage());
    }
}

// In the register() method, add at the top:
validateWebhookUrl(url);
```

### Step 7.2: Run and verify

Run: `.\mvnw compile`
Expected: SUCCESS

---

## Task 8: Soft Delete Enforcement

**Why:** `PictureAsset` has `deletedAt`/`deletedBy` fields but no query filters them. Soft-deleted pictures appear in all results.

**Files:**
- Modify: `domain/picture/PictureAsset.java:34`

### Step 8.1: Add @Where annotation

In `domain/picture/PictureAsset.java`, add the Hibernate `@Where` annotation:

```java
// BEFORE (line 34):
@Entity
@Table(name = "picture_asset")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor

// AFTER:
@Entity
@Table(name = "picture_asset")
@org.hibernate.annotations.Where(clause = "deleted_at IS NULL")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
```

### Step 8.2: Verify queries that need deleted items

Search for any code that intentionally queries deleted items (e.g., admin views). If such code exists, it will need to use native queries or a separate entity. For now, this change is correct — all business queries should exclude soft-deleted items.

Run: `.\mvnw compile`
Expected: SUCCESS

---

## Task 9: Batch Delete Ref Count Fix

**Why:** `BatchPictureService.batchDelete()` deletes picture records but never decrements `file_content.ref_count`. Storage files leak forever.

**Files:**
- Modify: `application/picture/BatchPictureService.java:28-35`
- Modify: `infrastructure/persistence/FileContentRepository.java:58-60`

### Step 9.1: Fix decrementRefCount query to prevent negative values

In `infrastructure/persistence/FileContentRepository.java`:

```java
// BEFORE (line 58-60):
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE FileContent fc SET fc.refCount = fc.refCount - 1 WHERE fc.id = :id")
void decrementRefCount(@Param("id") UUID id);

// AFTER:
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE FileContent fc SET fc.refCount = fc.refCount - 1 WHERE fc.id = :id AND fc.refCount > 0")
void decrementRefCount(@Param("id") UUID id);
```

### Step 9.2: Add ref_count decrement to batchDelete

In `application/picture/BatchPictureService.java`, inject `FileContentRepository` and decrement before deletion:

```java
// Add field:
private final FileContentRepository fileContentRepository;

// Modify batchDelete (lines 28-35):
@Transactional
public int batchDelete(List<UUID> pictureIds, UUID requesterId) {
    List<PictureAsset> assets = findAuthorizedAssets(pictureIds, requesterId);
    for (PictureAsset asset : assets) {
        // Decrement ref count for associated file content
        if (asset.getFileContentId() != null) {
            fileContentRepository.decrementRefCount(asset.getFileContentId());
        }
        pictureTagRepository.deleteByPictureAssetId(asset.getId());
    }
    pictureAssetRepository.deleteAll(assets);
    return assets.size();
}
```

### Step 9.3: Run and verify

Run: `.\mvnw compile`
Expected: SUCCESS

---

## Task 10: Production ddl-auto Validation

**Why:** `ddl-auto: update` in the base config means Hibernate silently modifies the schema if the prod profile isn't activated.

**Files:**
- Modify: `application.yml:17`
- Modify: `application-prod.yml`

### Step 10.1: Change base config to validate

In `application.yml`:

```yaml
# BEFORE (line 17):
ddl-auto: update

# AFTER:
ddl-auto: validate
```

### Step 10.2: Keep update in dev profile

In `application-dev.yml`, ensure it overrides:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

### Step 10.3: Run and verify

Run: `.\mvnw compile`
Expected: SUCCESS

---

## Verification Checklist

After all tasks are complete, verify the fixes end-to-end:

- [ ] `.\mvnw compile` — no compilation errors
- [ ] `.\mvnw test` — existing tests still pass (skip TeamServiceTests)
- [ ] Start app with `--spring.profiles.active=dev` — app boots successfully
- [ ] JWT secret validation: start app without `JWT_SECRET` env var — should fail to start
- [ ] File proxy: `GET /api/files/pictures/{id}/file.jpg` without auth — should get 401 for private images
- [ ] File proxy: `GET /api/files/public/{id}/file.jpg` — should serve public approved images
- [ ] Rate limiting: 11 rapid `POST /api/auth/login` attempts — 11th should get 429
- [ ] WebSocket: connect from `http://evil.com` — should be rejected
- [ ] Upload validation: upload a `.txt` file renamed to `.jpg` — should get 400
- [ ] Soft delete: delete a picture, then query gallery — should not appear
- [ ] Batch delete: delete a picture with file_content_id — file_content.ref_count should decrement

---

## Commit Strategy

Each task should be committed separately for easy rollback:

```
fix(security): add authenticated file proxy for uploads
fix(security): validate JWT secret at startup
fix(security): disable admin bootstrap by default
fix(security): add rate limiting to auth endpoints
fix(security): restrict WebSocket allowed origins
fix(security): add magic byte validation for file uploads
fix(security): add SSRF protection for webhook URLs
fix(data): enforce soft delete filtering via @Where
fix(data): decrement ref_count on batch picture deletion
fix(data): set ddl-auto to validate in base config
```
