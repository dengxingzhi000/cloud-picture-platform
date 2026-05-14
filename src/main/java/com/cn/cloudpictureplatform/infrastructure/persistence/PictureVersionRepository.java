package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cn.cloudpictureplatform.domain.picture.PictureVersion;

public interface PictureVersionRepository extends JpaRepository<PictureVersion, UUID> {
    List<PictureVersion> findByPictureIdOrderByVersionDesc(UUID pictureId);

    Optional<PictureVersion> findByPictureIdAndVersion(UUID pictureId, int version);

    @Query("SELECT COALESCE(MAX(v.version), 0) FROM PictureVersion v WHERE v.pictureId = :pictureId")
    int findMaxVersionByPictureId(@Param("pictureId") UUID pictureId);
}
