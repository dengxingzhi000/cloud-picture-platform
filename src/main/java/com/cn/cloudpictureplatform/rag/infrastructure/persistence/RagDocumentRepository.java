package com.cn.cloudpictureplatform.rag.infrastructure.persistence;

import com.cn.cloudpictureplatform.rag.domain.RagDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RagDocumentRepository extends JpaRepository<RagDocument, UUID> {
    List<RagDocument> findByDeletedFalse();
    List<RagDocument> findByOriginalFilenameAndDeletedFalse(String filename);
}
