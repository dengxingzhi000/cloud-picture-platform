package com.cn.cloudpictureplatform.application.excalidraw;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.domain.excalidraw.ExcalidrawScene;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw.ExcalidrawSceneRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.excalidraw.ExcalidrawFileRepository;
import com.cn.cloudpictureplatform.application.shared.dto.CreateExcalidrawSceneRequest;
import com.cn.cloudpictureplatform.application.shared.dto.ExcalidrawSceneResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcalidrawSceneServiceTests {

    @Mock
    private ExcalidrawSceneRepository sceneRepository;

    @Mock
    private ExcalidrawFileRepository fileRepository;

    @Mock
    private PictureAssetRepository pictureAssetRepository;

    @Mock
    private SpaceRepository spaceRepository;

    private ExcalidrawSceneService service;

    @BeforeEach
    void setUp() {
        service = new ExcalidrawSceneService(sceneRepository, fileRepository, pictureAssetRepository, spaceRepository);
    }

    @Test
    void shouldCreateSceneWithPictureId() {
        UUID pictureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CreateExcalidrawSceneRequest request = new CreateExcalidrawSceneRequest();
        request.setPictureId(pictureId);
        request.setSceneName("Test Scene");

        ExcalidrawScene savedScene = ExcalidrawScene.builder()
                .pictureId(pictureId)
                .sceneName("Test Scene")
                .lastUpdatedByUserId(userId)
                .build();
        savedScene.setId(UUID.randomUUID());

        when(sceneRepository.save(any(ExcalidrawScene.class))).thenReturn(savedScene);

        ExcalidrawSceneResponse response = service.createScene(request, userId);

        assertNotNull(response.getId());
        assertEquals(pictureId, response.getPictureId());
        assertEquals("Test Scene", response.getSceneName());
        assertEquals(userId, response.getLastUpdatedByUserId());

        verify(pictureAssetRepository, never()).save(any());
    }

    @Test
    void shouldCreateWhiteboardSceneWithoutPictureId() {
        UUID userId = UUID.randomUUID();

        CreateExcalidrawSceneRequest request = new CreateExcalidrawSceneRequest();
        request.setSceneName("Whiteboard");

        PictureAsset whiteboardPicture = PictureAsset.builder()
                .ownerId(userId)
                .spaceId(UUID.randomUUID())
                .name("Whiteboard")
                .originalFilename("whiteboard.excalidraw")
                .contentType("application/x-excalidraw")
                .sizeBytes(0L)
                .storageKey("whiteboard:" + UUID.randomUUID())
                .build();
        whiteboardPicture.setId(UUID.randomUUID());

        Space personalSpace = Space.builder()
                .ownerId(userId).type(SpaceType.PERSONAL).name("Personal")
                .quotaBytes(10L * 1024 * 1024 * 1024).usedBytes(0L).build();
        personalSpace.setId(UUID.randomUUID());

        ExcalidrawScene savedScene = ExcalidrawScene.builder()
                .pictureId(whiteboardPicture.getId())
                .sceneName("Whiteboard")
                .lastUpdatedByUserId(userId)
                .build();
        savedScene.setId(UUID.randomUUID());

        when(spaceRepository.findFirstByOwnerIdAndType(userId, SpaceType.PERSONAL))
                .thenReturn(Optional.of(personalSpace));
        when(pictureAssetRepository.save(any(PictureAsset.class))).thenReturn(whiteboardPicture);
        when(sceneRepository.save(any(ExcalidrawScene.class))).thenReturn(savedScene);

        ExcalidrawSceneResponse response = service.createScene(request, userId);

        assertNotNull(response.getId());
        assertEquals(whiteboardPicture.getId(), response.getPictureId());
        assertEquals("Whiteboard", response.getSceneName());

        verify(pictureAssetRepository).save(any(PictureAsset.class));
    }

    @Test
    void shouldCreateSceneWithDefaultName() {
        UUID userId = UUID.randomUUID();

        CreateExcalidrawSceneRequest request = new CreateExcalidrawSceneRequest();
        request.setPictureId(UUID.randomUUID());

        ExcalidrawScene savedScene = ExcalidrawScene.builder()
                .pictureId(request.getPictureId())
                .sceneName("Untitled")
                .lastUpdatedByUserId(userId)
                .build();
        savedScene.setId(UUID.randomUUID());

        when(sceneRepository.save(any(ExcalidrawScene.class))).thenReturn(savedScene);

        ExcalidrawSceneResponse response = service.createScene(request, userId);

        assertEquals("Untitled", response.getSceneName());
    }

    @Test
    void shouldGetSceneById() {
        UUID sceneId = UUID.randomUUID();
        ExcalidrawScene scene = ExcalidrawScene.builder()
                .pictureId(UUID.randomUUID())
                .sceneName("Test")
                .lastUpdatedByUserId(UUID.randomUUID())
                .build();
        scene.setId(sceneId);

        when(sceneRepository.findById(sceneId)).thenReturn(Optional.of(scene));

        ExcalidrawSceneResponse response = service.getScene(sceneId);

        assertEquals(sceneId, response.getId());
        assertEquals("Test", response.getSceneName());
    }

    @Test
    void shouldThrowWhenSceneNotFound() {
        UUID sceneId = UUID.randomUUID();
        when(sceneRepository.findById(sceneId)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.getScene(sceneId));
    }

    @Test
    void shouldUpdateSnapshot() {
        UUID sceneId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String snapshotData = "{\"elements\":[],\"files\":{}}";

        ExcalidrawScene scene = ExcalidrawScene.builder()
                .pictureId(UUID.randomUUID())
                .sceneName("Test")
                .lastUpdatedByUserId(UUID.randomUUID())
                .build();
        scene.setId(sceneId);

        when(sceneRepository.findById(sceneId)).thenReturn(Optional.of(scene));
        when(sceneRepository.save(any(ExcalidrawScene.class))).thenReturn(scene);

        ExcalidrawSceneResponse response = service.updateSnapshot(sceneId, snapshotData, 10L, userId);

        assertEquals(snapshotData, response.getSnapshotData());
        assertEquals(userId, response.getLastUpdatedByUserId());
    }

    @Test
    void shouldDeleteScene() {
        UUID sceneId = UUID.randomUUID();

        service.deleteScene(sceneId);

        verify(fileRepository).deleteBySceneId(sceneId);
        verify(sceneRepository).deleteById(sceneId);
    }
}
