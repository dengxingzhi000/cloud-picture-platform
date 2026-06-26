package com.cn.cloudpictureplatform.interfaces.ai;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.picture.PictureQueryService;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.ai.AiToolCallAudit;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiToolCallAuditRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai/tools")
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiToolsController {
    private final PictureQueryService pictureQueryService;
    private final AiToolCallAuditRepository toolCallAuditRepository;

    public AiToolsController(PictureQueryService pictureQueryService,
                             AiToolCallAuditRepository toolCallAuditRepository) {
        this.pictureQueryService = pictureQueryService;
        this.toolCallAuditRepository = toolCallAuditRepository;
    }

    @GetMapping("/search-pictures")
    public Map<String, Object> searchPictures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String orientation,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String sessionId
    ) {
        long start = System.currentTimeMillis();
        try {
            var result = pictureQueryService.searchPictures(
                    page, size, keyword, null, null,
                    visibility != null ? com.cn.cloudpictureplatform.domain.picture.Visibility.valueOf(visibility) : null,
                    null, null, null, null, null, orientation, tag, null,
                    sortBy, sortDir, null, null
            );
            long latencyMs = System.currentTimeMillis() - start;
            auditToolCall(sessionId, "search_pictures",
                    Map.of("keyword", keyword != null ? keyword : "", "tag", tag != null ? tag : ""),
                    result.getTotal(), true, latencyMs, null);
            return Map.of(
                    "total", result.getTotal(),
                    "page", result.getPage(),
                    "size", result.getSize(),
                    "items", result.getItems()
            );
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            auditToolCall(sessionId, "search_pictures",
                    Map.of("keyword", keyword != null ? keyword : ""),
                    null, false, latencyMs, e.getMessage());
            throw e;
        }
    }

    private void auditToolCall(String sessionId, String toolName, Object toolArgs,
                                Long resultSize, boolean success, long latencyMs, String errorMessage) {
        try {
            toolCallAuditRepository.save(AiToolCallAudit.builder()
                    .sessionId(sessionId != null ? sessionId : "unknown")
                    .toolName(toolName)
                    .toolArgs(toolArgs != null ? toolArgs.toString() : null)
                    .resultSize(resultSize != null ? resultSize.intValue() : null)
                    .success(success)
                    .latencyMs((int) latencyMs)
                    .errorMessage(errorMessage)
                    .createdAt(java.time.Instant.now())
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to audit tool call: {}", ex.getMessage());
        }
    }
}
