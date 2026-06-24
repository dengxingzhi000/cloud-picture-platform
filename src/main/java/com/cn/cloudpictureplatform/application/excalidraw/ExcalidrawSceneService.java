package com.cn.cloudpictureplatform.application.excalidraw;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.excalidraw.ExcalidrawScene;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw.ExcalidrawSceneRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw.ExcalidrawFileRepository;
import com.cn.cloudpictureplatform.application.shared.dto.CreateExcalidrawSceneRequest;
import com.cn.cloudpictureplatform.application.shared.dto.ExcalidrawSceneResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExcalidrawSceneService {

    private final ExcalidrawSceneRepository sceneRepository;
    private final ExcalidrawFileRepository fileRepository;
    private final PictureAssetRepository pictureAssetRepository;
    private final SpaceRepository spaceRepository;

    @Transactional
    public ExcalidrawSceneResponse createScene(CreateExcalidrawSceneRequest request, UUID userId) {
        UUID pictureId = request.getPictureId();

        // Whiteboard mode: create a placeholder PictureAsset for unified management
        if (pictureId == null) {
            Space personalSpace = spaceRepository
                    .findFirstByOwnerIdAndType(userId, SpaceType.PERSONAL)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "personal space not found for user"));

            PictureAsset whiteboardPicture = PictureAsset.builder()
                    .ownerId(userId)
                    .spaceId(personalSpace.getId())
                    .name(request.getSceneName() != null ? request.getSceneName() : "Untitled Whiteboard")
                    .originalFilename("whiteboard.excalidraw")
                    .contentType("application/x-excalidraw")
                    .sizeBytes(0L)
                    .storageKey("whiteboard:" + UUID.randomUUID())
                    .visibility(Visibility.PRIVATE)
                    .reviewStatus(ReviewStatus.PENDING)
                    .build();
            pictureId = pictureAssetRepository.save(whiteboardPicture).getId();
        }

        ExcalidrawScene scene = ExcalidrawScene.builder()
                .pictureId(pictureId)
                .sceneName(request.getSceneName() != null ? request.getSceneName() : "Untitled")
                .lastUpdatedByUserId(userId)
                .build();
        scene = sceneRepository.save(scene);
        return toResponse(scene);
    }

    @Transactional(readOnly = true)
    public ExcalidrawSceneResponse getScene(UUID sceneId) {
        ExcalidrawScene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "Scene not found"));
        return toResponse(scene);
    }

    @Transactional(readOnly = true)
    public ExcalidrawSceneResponse getSceneByPictureId(UUID pictureId) {
        ExcalidrawScene scene = sceneRepository.findByPictureId(pictureId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "Scene not found for picture"));
        return toResponse(scene);
    }

    @Transactional
    public ExcalidrawSceneResponse updateSnapshot(UUID sceneId, String snapshotData, Long lastSeq, UUID userId) {
        ExcalidrawScene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "Scene not found"));
        scene.setSnapshotData(snapshotData);
        if (lastSeq != null) {
            scene.setLastSeq(lastSeq);
        }
        scene.setLastUpdatedByUserId(userId);
        scene = sceneRepository.save(scene);
        return toResponse(scene);
    }

    @Transactional
    public void deleteScene(UUID sceneId) {
        fileRepository.deleteBySceneId(sceneId);
        sceneRepository.deleteById(sceneId);
    }

    private ExcalidrawSceneResponse toResponse(ExcalidrawScene scene) {
        return ExcalidrawSceneResponse.builder()
                .id(scene.getId())
                .pictureId(scene.getPictureId())
                .sceneName(scene.getSceneName())
                .snapshotData(scene.getSnapshotData())
                .version(scene.getVersion())
                .lastSeq(scene.getLastSeq())
                .lastUpdatedByUserId(scene.getLastUpdatedByUserId())
                .createdAt(scene.getCreatedAt())
                .updatedAt(scene.getUpdatedAt())
                .build();
    }
}
