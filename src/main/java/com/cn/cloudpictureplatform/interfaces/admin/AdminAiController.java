package com.cn.cloudpictureplatform.interfaces.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiCallAuditRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiModerationRecordRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.domain.ai.AiCallAudit;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.interfaces.admin.dto.AiStatsResponse;

@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiController {
    private final AiCallAuditRepository aiCallAuditRepository;
    private final AiModerationRecordRepository aiModerationRecordRepository;
    private final PictureAssetRepository pictureAssetRepository;

    public AdminAiController(
            AiCallAuditRepository aiCallAuditRepository,
            AiModerationRecordRepository aiModerationRecordRepository,
            PictureAssetRepository pictureAssetRepository
    ) {
        this.aiCallAuditRepository = aiCallAuditRepository;
        this.aiModerationRecordRepository = aiModerationRecordRepository;
        this.pictureAssetRepository = pictureAssetRepository;
    }

    @GetMapping("/stats")
    public ApiResponse<AiStatsResponse> getStats() {
        var audits = aiCallAuditRepository.findAll();
        var taggingAudits = audits.stream().filter(a -> "tagging".equals(a.getTaskType())).toList();
        var embeddingAudits = audits.stream().filter(a -> "embedding".equals(a.getTaskType())).toList();
        var moderationAudits = audits.stream().filter(a -> "moderation".equals(a.getTaskType())).toList();

        long autoApproved = pictureAssetRepository.findByReviewStatus(ReviewStatus.AUTO_APPROVED, PageRequest.of(0, 1)).getTotalElements();
        long autoRejected = pictureAssetRepository.findByReviewStatus(ReviewStatus.AUTO_REJECTED, PageRequest.of(0, 1)).getTotalElements();
        var modRecords = aiModerationRecordRepository.findAll();
        double avgConfidence = modRecords.stream()
                .mapToDouble(r -> r.getConfidence() != null ? r.getConfidence() : 0.0)
                .average().orElse(0.0);

        return ApiResponse.ok(AiStatsResponse.builder()
                .tagging(AiStatsResponse.TaggingStats.builder()
                        .totalCalls(taggingAudits.size())
                        .successCalls(taggingAudits.stream().filter(AiCallAudit::isSuccess).count())
                        .avgLatencyMs(taggingAudits.stream()
                                .mapToInt(a -> a.getLatencyMs() != null ? a.getLatencyMs() : 0)
                                .average().orElse(0.0))
                        .build())
                .moderation(AiStatsResponse.ModerationStats.builder()
                        .totalCalls(moderationAudits.size())
                        .autoApproved(autoApproved)
                        .autoRejected(autoRejected)
                        .avgConfidence(avgConfidence)
                        .build())
                .embedding(AiStatsResponse.EmbeddingStats.builder()
                        .totalCalls(embeddingAudits.size())
                        .avgLatencyMs(embeddingAudits.stream()
                                .mapToInt(a -> a.getLatencyMs() != null ? a.getLatencyMs() : 0)
                                .average().orElse(0.0))
                        .build())
                .build());
    }
}
