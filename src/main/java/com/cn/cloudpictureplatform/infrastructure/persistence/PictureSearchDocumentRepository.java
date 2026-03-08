package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cn.cloudpictureplatform.domain.search.PictureSearchDocument;

public interface PictureSearchDocumentRepository extends JpaRepository<PictureSearchDocument, UUID> {
}
