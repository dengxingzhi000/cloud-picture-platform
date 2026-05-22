package com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw;

import com.cn.cloudpictureplatform.domain.excalidraw.ExcalidrawFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExcalidrawFileRepository extends JpaRepository<ExcalidrawFile, UUID> {

    List<ExcalidrawFile> findBySceneId(UUID sceneId);

    Optional<ExcalidrawFile> findBySceneIdAndFileId(UUID sceneId, String fileId);

    void deleteBySceneId(UUID sceneId);
}
