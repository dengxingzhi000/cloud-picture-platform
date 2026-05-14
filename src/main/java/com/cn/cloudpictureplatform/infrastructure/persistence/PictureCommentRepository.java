package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cn.cloudpictureplatform.domain.picture.PictureComment;

public interface PictureCommentRepository extends JpaRepository<PictureComment, UUID> {
    List<PictureComment> findByPictureIdOrderByCreatedAtAsc(UUID pictureId);

    List<PictureComment> findByParentIdOrderByCreatedAtAsc(UUID parentId);

    @Query("SELECT COUNT(c) FROM PictureComment c WHERE c.parentId = :parentId")
    int countByParentId(@Param("parentId") UUID parentId);
}
