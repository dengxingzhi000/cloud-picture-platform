package com.cn.cloudpictureplatform.rag.infrastructure.persistence;

import com.cn.cloudpictureplatform.rag.domain.DocumentChunk;
import com.cn.cloudpictureplatform.rag.domain.ChunkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    List<DocumentChunk> findByDocumentIdAndStatusOrderByChunkIndex(UUID documentId, ChunkStatus status);

    @Modifying
    @Query("UPDATE DocumentChunk c SET c.status = :status WHERE c.document.id = :documentId")
    void updateStatusByDocumentId(UUID documentId, ChunkStatus status);
}
