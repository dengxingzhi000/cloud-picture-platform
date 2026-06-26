package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cn.cloudpictureplatform.domain.ai.AiChatHistory;

public interface AiChatHistoryRepository extends JpaRepository<AiChatHistory, UUID> {

    List<AiChatHistory> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    @Modifying
    @Query("DELETE FROM AiChatHistory h WHERE h.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM AiChatHistory h WHERE h.createdAt < :cutoff")
    void deleteOlderThan(@Param("cutoff") java.time.Instant cutoff);
}
