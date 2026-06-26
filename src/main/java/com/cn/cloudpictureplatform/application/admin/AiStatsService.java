package com.cn.cloudpictureplatform.application.admin;

import com.cn.cloudpictureplatform.domain.ai.AiCallAudit;
import com.cn.cloudpictureplatform.domain.ai.AiToolCallAudit;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiCallAuditRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiModerationRecordRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiToolCallAuditRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.application.shared.dto.AiStatsResponse;
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
    private final AiToolCallAuditRepository aiToolCallAuditRepository;
    private final AiModerationRecordRepository aiModerationRecordRepository;
    private final PictureAssetRepository pictureAssetRepository;

    public AiStatsResponse getStats() {
        List<AiCallAudit> audits = aiCallAuditRepository.findAll();
        List<AiCallAudit> taggingAudits = audits.stream().filter(a -> "tagging".equals(a.getTaskType())).toList();
        List<AiCallAudit> embeddingAudits = audits.stream().filter(a -> "embedding".equals(a.getTaskType())).toList();
        List<AiCallAudit> moderationAudits = audits.stream().filter(a -> "moderation".equals(a.getTaskType())).toList();
        List<AiCallAudit> chatAudits = audits.stream().filter(a -> "chat".equals(a.getTaskType())).toList();

        long autoApproved = pictureAssetRepository.findByReviewStatus(ReviewStatus.AUTO_APPROVED, PageRequest.of(0, 1)).getTotalElements();
        long autoRejected = pictureAssetRepository.findByReviewStatus(ReviewStatus.AUTO_REJECTED, PageRequest.of(0, 1)).getTotalElements();
        var modRecords = aiModerationRecordRepository.findAll();
        double avgConfidence = modRecords.stream()
                .mapToDouble(r -> r.getConfidence() != null ? r.getConfidence() : 0.0)
                .average().orElse(0.0);

        List<AiToolCallAudit> toolCalls = aiToolCallAuditRepository.findAll();

        long chatSuccessCount = chatAudits.stream().filter(AiCallAudit::isSuccess).count();
        long totalTokens = chatAudits.stream()
                .mapToLong(a -> a.getTokensUsed() != null ? a.getTokensUsed() : 0)
                .sum();
        long chatWithTools = chatAudits.stream()
                .filter(a -> a.getToolCallsCount() != null && a.getToolCallsCount() > 0)
                .count();

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
                .chat(AiStatsResponse.ChatStats.builder()
                        .totalCalls(chatAudits.size())
                        .successCalls(chatSuccessCount)
                        .avgLatencyMs(chatAudits.stream()
                                .mapToInt(a -> a.getLatencyMs() != null ? a.getLatencyMs() : 0)
                                .average().orElse(0.0))
                        .totalTokensUsed(totalTokens)
                        .avgTokensPerCall(chatAudits.isEmpty() ? 0 : (double) totalTokens / chatAudits.size())
                        .toolCallRate(chatAudits.isEmpty() ? 0 : (double) chatWithTools / chatAudits.size())
                        .build())
                .toolCalls(AiStatsResponse.ToolCallStats.builder()
                        .totalCalls(toolCalls.size())
                        .successCalls(toolCalls.stream().filter(AiToolCallAudit::isSuccess).count())
                        .avgLatencyMs(toolCalls.stream()
                                .mapToInt(t -> t.getLatencyMs() != null ? t.getLatencyMs() : 0)
                                .average().orElse(0.0))
                        .build())
                .build();
    }
}
