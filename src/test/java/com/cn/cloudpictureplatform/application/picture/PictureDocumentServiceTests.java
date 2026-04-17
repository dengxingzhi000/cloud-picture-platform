package com.cn.cloudpictureplatform.application.picture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.domain.picture.PictureEditorDocument;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureEditorDocumentRepository;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureEditorDocumentResponse;
import com.cn.cloudpictureplatform.websocket.dto.CollabMessage;
import com.cn.cloudpictureplatform.websocket.dto.PictureDocumentOperationPayload;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@ExtendWith(MockitoExtension.class)
class PictureDocumentServiceTests {

    @Mock
    private PictureAssetRepository pictureAssetRepository;

    @Mock
    private PictureEditorDocumentRepository pictureEditorDocumentRepository;

    private PictureDocumentService service;

    @BeforeEach
    void setUp() {
        service = new PictureDocumentService(
                pictureAssetRepository,
                pictureEditorDocumentRepository,
                Jackson2ObjectMapperBuilder.json().build()
        );
    }

    @Test
    void shouldReturnEmptySnapshotWhenDocumentDoesNotExist() {
        UUID pictureId = UUID.randomUUID();
        when(pictureAssetRepository.existsById(pictureId)).thenReturn(true);
        when(pictureEditorDocumentRepository.findByPictureId(pictureId)).thenReturn(Optional.empty());

        PictureEditorDocumentResponse response = service.getSnapshot(pictureId);

        assertEquals(pictureId, response.pictureId());
        assertEquals(PictureDocumentService.DOCUMENT_SCHEMA_VERSION, response.schemaVersion());
        assertEquals(0L, response.version());
        assertTrue(response.elements().isEmpty());
    }

    @Test
    void shouldPersistAddAndUpdateOperations() {
        UUID pictureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AtomicReference<PictureEditorDocument> stored = new AtomicReference<>();

        when(pictureAssetRepository.existsById(pictureId)).thenReturn(true);
        when(pictureEditorDocumentRepository.findByPictureId(pictureId))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(pictureEditorDocumentRepository.saveAndFlush(any(PictureEditorDocument.class)))
                .thenAnswer(invocation -> {
                    PictureEditorDocument document = invocation.getArgument(0);
                    if (document.getUpdatedAt() == null) {
                        document.setUpdatedAt(Instant.parse("2026-04-01T00:00:00Z"));
                    }
                    stored.set(document);
                    return document;
                });

        PictureDocumentOperationPayload addPayload = new PictureDocumentOperationPayload();
        addPayload.setId("rect-1");
        addPayload.setType("rect");
        addPayload.setX(12.0);
        addPayload.setY(18.0);
        addPayload.setWidth(120.0);
        addPayload.setHeight(80.0);

        PictureDocumentService.AppliedOperation added = service.applyOperation(
                pictureId,
                userId,
                CollabMessage.EventType.ELEMENT_ADD,
                addPayload
        );

        assertEquals(1L, added.version());
        assertEquals("rect-1", added.element().id());
        assertTrue(stored.get().getDocumentContent().contains("\"rect-1\""));

        PictureDocumentOperationPayload updatePayload = new PictureDocumentOperationPayload();
        updatePayload.setId("rect-1");
        updatePayload.setType("rect");
        updatePayload.setX(20.0);
        updatePayload.setY(24.0);
        updatePayload.setWidth(144.0);
        updatePayload.setHeight(96.0);

        PictureDocumentService.AppliedOperation updated = service.applyOperation(
                pictureId,
                userId,
                CollabMessage.EventType.ELEMENT_UPDATE,
                updatePayload
        );

        assertEquals(2L, updated.version());
        assertEquals(20.0, updated.element().x());
        assertTrue(stored.get().getDocumentContent().contains("\"width\":144.0"));
    }

    @Test
    void shouldRejectTextElementWithoutTextContent() {
        UUID pictureId = UUID.randomUUID();
        when(pictureAssetRepository.existsById(pictureId)).thenReturn(true);
        when(pictureEditorDocumentRepository.findByPictureId(pictureId)).thenReturn(Optional.empty());

        PictureDocumentOperationPayload payload = new PictureDocumentOperationPayload();
        payload.setId("text-1");
        payload.setType("text");
        payload.setX(8.0);
        payload.setY(10.0);
        payload.setWidth(100.0);
        payload.setHeight(24.0);

        assertThrows(
                ApiException.class,
                () -> service.applyOperation(pictureId, UUID.randomUUID(), CollabMessage.EventType.ELEMENT_ADD, payload)
        );
    }
}
