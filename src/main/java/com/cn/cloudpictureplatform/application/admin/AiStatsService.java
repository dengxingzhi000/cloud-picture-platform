package com.cn.cloudpictureplatform.application.admin;

import com.cn.cloudpictureplatform.domain.ai.AiCallAudit;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiCallAuditRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiModerationRecordRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.interfaces.admin.dto.AiStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiStatsService {

    private final AiCallAuditRepository aiCallAuditRepository;
    private final AiModerationRecordRepository aiModerationRecordRepository;
    private final PictureAssetRepository pictureAssetRepository;

    public AiStatsResponse getStats() {
        List<AiCallAudit> audits = aiCallAuditRepository.findAll();
        List<AiCallAudit> taggingAudits = audits.stream().filter(a -> "tagging".equals(a.getTaskType())).toList();
        List<AiCallAudit> embeddingAudits = audits.stream().filter(a -> "embedding".equals(a.getTaskType())).toList();
        List<AiCallAudit> moderationAudits = audits.stream().filter(a -> "moderation".equals(a.getTaskType())).toList();

        long autoApproved = pictureAssetRepository.findByReviewStatus(ReviewStatus.AUTO_APPROVED, PageRequest.of(0, 1)).getTotalElements();
        long autoRejected = pictureAssetRepository.findByReviewStatus(ReviewStatus.AUTO_REJECTED, PageRequest.of(0, 1)).getTotalElements();
        var modRecords = aiModerationRecordRepository.findAll();
        double avgConfidence = modRecords.stream()
                .mapToDouble(r -> r.getConfidence() != null ? r.getConfidence() : 0.0)
                .average().orElse(0.0);

        return AiStatsResponse.builder()
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
                .build();
    }
}
