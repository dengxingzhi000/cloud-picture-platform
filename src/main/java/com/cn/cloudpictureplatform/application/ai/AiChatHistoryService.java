package com.cn.cloudpictureplatform.application.ai;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.domain.ai.AiChatHistory;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiChatHistoryRepository;

@Service
@Transactional(readOnly = true)
public class AiChatHistoryService {

    private final AiChatHistoryRepository repository;

    public AiChatHistoryService(AiChatHistoryRepository repository) {
        this.repository = repository;
    }

    public List<AiChatHistory> getHistory(String sessionId) {
        return repository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void saveMessage(String sessionId, UUID userId, String role, String content,
                            String intent, List<UUID> contextPictureIds) {
        String contextIds = contextPictureIds != null
                ? contextPictureIds.stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse(null)
                : null;
        repository.save(AiChatHistory.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role(role)
                .content(content)
                .intent(intent)
                .contextPictureIds(contextIds)
                .build());
    }

    @Transactional
    public void cleanupOldHistory(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        repository.deleteOlderThan(cutoff);
    }
}
