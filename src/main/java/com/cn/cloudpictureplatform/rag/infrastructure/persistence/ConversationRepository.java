package com.cn.cloudpictureplatform.rag.infrastructure.persistence;

import com.cn.cloudpictureplatform.rag.domain.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConversationRepository extends JpaRepository<ConversationMessage, java.util.UUID> {
    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}