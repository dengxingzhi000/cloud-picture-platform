package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.picture.PictureEditorDocument;

public interface PictureEditorDocumentRepository extends JpaRepository<PictureEditorDocument, UUID> {
    Optional<PictureEditorDocument> findByPictureId(UUID pictureId);
}
