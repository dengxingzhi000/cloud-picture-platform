package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.search.PictureSearchDocument;

public interface PictureSearchDocumentRepository extends JpaRepository<PictureSearchDocument, UUID> {

    List<PictureSearchDocument> findByContentContainingIgnoreCase(String content, Pageable pageable);

    @Query(value = "SELECT picture_id FROM picture_search_document ORDER BY image_embedding <=> CAST(:vector AS vector) LIMIT :limit", nativeQuery = true)
    List<UUID> findByVectorDistance(@Param("vector") String vector, @Param("limit") int limit);

    @Query("SELECT p FROM PictureAsset p WHERE p.id IN :ids")
    List<PictureAsset> findPictureAssetsByIds(@Param("ids") List<UUID> ids);
}
