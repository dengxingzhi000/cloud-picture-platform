package com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw;

import com.cn.cloudpictureplatform.domain.excalidraw.ExcalidrawScene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExcalidrawSceneRepository extends JpaRepository<ExcalidrawScene, UUID> {

    Optional<ExcalidrawScene> findByPictureId(UUID pictureId);

    List<ExcalidrawScene> findByPictureIdIsNullOrderByUpdatedAtDesc();

    @Modifying
    @Query("UPDATE ExcalidrawScene s SET s.snapshotData = :snapshotData, s.lastSeq = :lastSeq, s.lastUpdatedByUserId = :userId WHERE s.id = :id")
    int updateSnapshot(@Param("id") UUID id, @Param("snapshotData") String snapshotData, @Param("lastSeq") Long lastSeq, @Param("userId") UUID userId);
}
