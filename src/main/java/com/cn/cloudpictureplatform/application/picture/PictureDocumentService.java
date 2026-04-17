package com.cn.cloudpictureplatform.application.picture;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.picture.PictureEditorDocument;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureEditorDocumentRepository;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureDocumentElementResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureEditorDocumentResponse;
import com.cn.cloudpictureplatform.websocket.dto.CollabMessage;
import com.cn.cloudpictureplatform.websocket.dto.PictureDocumentOperationPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PictureDocumentService {

    public static final String DOCUMENT_SCHEMA_VERSION = "picture-editor-document.v1";

    private static final TypeReference<List<EditorElement>> ELEMENT_LIST_TYPE = new TypeReference<>() {
    };

    private final PictureAssetRepository pictureAssetRepository;
    private final PictureEditorDocumentRepository pictureEditorDocumentRepository;
    private final ObjectMapper objectMapper;

    public PictureDocumentService(
            PictureAssetRepository pictureAssetRepository,
            PictureEditorDocumentRepository pictureEditorDocumentRepository,
            ObjectMapper objectMapper
    ) {
        this.pictureAssetRepository = pictureAssetRepository;
        this.pictureEditorDocumentRepository = pictureEditorDocumentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PictureEditorDocumentResponse getSnapshot(UUID pictureId) {
        verifyPictureExists(pictureId);
        PictureEditorDocument document = pictureEditorDocumentRepository.findByPictureId(pictureId)
                .orElse(null);
        if (document == null) {
            return PictureEditorDocumentResponse.builder()
                    .pictureId(pictureId)
                    .schemaVersion(DOCUMENT_SCHEMA_VERSION)
                    .version(0L)
                    .elements(List.of())
                    .build();
        }
        return toResponse(document, deserialize(document.getDocumentContent()));
    }

    @Transactional
    public AppliedOperation applyOperation(
            UUID pictureId,
            UUID userId,
            CollabMessage.EventType eventType,
            PictureDocumentOperationPayload payload
    ) {
        verifyPictureExists(pictureId);
        if (eventType != CollabMessage.EventType.ELEMENT_ADD
                && eventType != CollabMessage.EventType.ELEMENT_UPDATE
                && eventType != CollabMessage.EventType.ELEMENT_REMOVE) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "unsupported document operation");
        }
        if (payload == null || !StringUtils.hasText(payload.getId())) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "element id is required");
        }

        PictureEditorDocument document = pictureEditorDocumentRepository.findByPictureId(pictureId)
                .orElseGet(() -> PictureEditorDocument.builder()
                        .pictureId(pictureId)
                        .version(0L)
                        .rowVersion(0L)
                        .documentContent("[]")
                        .build());
        List<EditorElement> elements = new ArrayList<>(deserialize(document.getDocumentContent()));
        List<EditorElement> originalElements = List.copyOf(elements);
        validateBaseVersion(eventType, payload, document, elements);
        EditorElement resolved = switch (eventType) {
            case ELEMENT_ADD -> applyAdd(elements, payload);
            case ELEMENT_UPDATE -> applyUpdate(elements, payload);
            case ELEMENT_REMOVE -> applyRemove(elements, payload);
            default -> throw new ApiException(ApiErrorCode.BAD_REQUEST, "unsupported document operation");
        };

        if (isNoop(eventType, payload, originalElements, elements, resolved)) {
            return new AppliedOperation(document.getVersion(), document.getUpdatedAt(), toElementResponse(resolved));
        }

        document.setVersion(document.getVersion() + 1);
        document.setLastUpdatedByUserId(userId);
        document.setDocumentContent(serialize(elements));
        try {
            PictureEditorDocument saved = pictureEditorDocumentRepository.saveAndFlush(document);
            return new AppliedOperation(saved.getVersion(), saved.getUpdatedAt(), toElementResponse(resolved));
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException ex) {
            throw new ApiException(ApiErrorCode.CONFLICT, "document version conflict");
        }
    }

    private EditorElement applyAdd(List<EditorElement> elements, PictureDocumentOperationPayload payload) {
        EditorElement existing = findElement(elements, payload.getId());
        if (existing != null) {
            EditorElement requested = toNewElement(payload);
            if (existing.equals(requested)) {
                return existing;
            }
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "element already exists");
        }
        EditorElement element = toNewElement(payload);
        elements.add(element);
        return element;
    }

    private EditorElement applyUpdate(List<EditorElement> elements, PictureDocumentOperationPayload payload) {
        EditorElement existing = findElement(elements, payload.getId());
        if (existing == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "element not found");
        }
        if (StringUtils.hasText(payload.getType()) && !existing.type().equals(payload.getType().trim().toLowerCase())) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "element type mismatch");
        }
        EditorElement updated = existing.applyUpdate(payload);
        validateElement(updated);
        replaceElement(elements, updated);
        return updated;
    }

    private EditorElement applyRemove(List<EditorElement> elements, PictureDocumentOperationPayload payload) {
        EditorElement existing = findElement(elements, payload.getId());
        if (existing == null) {
            return new EditorElement(payload.getId().trim(), "rect", null, null, null, null, null);
        }
        elements.removeIf(element -> Objects.equals(element.id(), payload.getId()));
        return existing;
    }

    private void validateBaseVersion(
            CollabMessage.EventType eventType,
            PictureDocumentOperationPayload payload,
            PictureEditorDocument document,
            List<EditorElement> elements
    ) {
        if (payload.getBaseVersion() == null || payload.getBaseVersion() == document.getVersion()) {
            return;
        }
        switch (eventType) {
            case ELEMENT_ADD -> validateStaleAdd(payload, elements);
            case ELEMENT_UPDATE -> validateStaleUpdate(payload, elements);
            case ELEMENT_REMOVE -> validateStaleRemove(payload, elements);
            default -> throw conflict(payload, document);
        }
    }

    private void validateStaleAdd(PictureDocumentOperationPayload payload, List<EditorElement> elements) {
        EditorElement existing = findElement(elements, payload.getId());
        if (existing == null) {
            return;
        }
        EditorElement requested = toNewElement(payload);
        if (!existing.equals(requested)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "element already exists with a different state");
        }
    }

    private void validateStaleUpdate(PictureDocumentOperationPayload payload, List<EditorElement> elements) {
        EditorElement existing = findElement(elements, payload.getId());
        if (existing == null) {
            throw new ApiException(ApiErrorCode.CONFLICT, "element no longer exists");
        }
        if (StringUtils.hasText(payload.getType()) && !existing.type().equals(payload.getType().trim().toLowerCase())) {
            throw new ApiException(ApiErrorCode.CONFLICT, "element type changed");
        }
        EditorElement updated = existing.applyUpdate(payload);
        validateElement(updated);
    }

    private void validateStaleRemove(PictureDocumentOperationPayload payload, List<EditorElement> elements) {
        if (findElement(elements, payload.getId()) == null) {
            return;
        }
    }

    private boolean isNoop(
            CollabMessage.EventType eventType,
            PictureDocumentOperationPayload payload,
            List<EditorElement> originalElements,
            List<EditorElement> elements,
            EditorElement resolved
    ) {
        return switch (eventType) {
            case ELEMENT_ADD -> {
                EditorElement existingBefore = findElement(originalElements, payload.getId());
                yield existingBefore != null && existingBefore.equals(resolved);
            }
            case ELEMENT_UPDATE -> {
                EditorElement existingBefore = findElement(originalElements, payload.getId());
                EditorElement current = findElement(elements, payload.getId());
                yield existingBefore != null && current != null && existingBefore.equals(current);
            }
            case ELEMENT_REMOVE -> findElement(elements, payload.getId()) == null && resolved.x() == null;
            default -> false;
        };
    }

    private ApiException conflict(PictureDocumentOperationPayload payload, PictureEditorDocument document) {
        return new ApiException(
                ApiErrorCode.CONFLICT,
                "document version conflict: expected %d but was %d"
                        .formatted(payload.getBaseVersion(), document.getVersion())
        );
    }

    private EditorElement toNewElement(PictureDocumentOperationPayload payload) {
        String normalizedType = normalizeType(payload.getType());
        EditorElement element = new EditorElement(
                payload.getId().trim(),
                normalizedType,
                requiredNumber(payload.getX(), "x"),
                requiredNumber(payload.getY(), "y"),
                requiredNumber(payload.getWidth(), "width"),
                requiredNumber(payload.getHeight(), "height"),
                normalizedType.equals("text") ? normalizeText(payload.getText()) : null
        );
        validateElement(element);
        return element;
    }

    private void validateElement(EditorElement element) {
        if (!StringUtils.hasText(element.id())) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "element id is required");
        }
        if (!"rect".equals(element.type()) && !"text".equals(element.type())) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "unsupported element type");
        }
        if (element.width() == null || element.width() < 1) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "width must be at least 1");
        }
        if (element.height() == null || element.height() < 1) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "height must be at least 1");
        }
        if (element.x() == null || element.y() == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "x and y are required");
        }
        if ("text".equals(element.type()) && !StringUtils.hasText(element.text())) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "text content is required");
        }
    }

    private EditorElement findElement(List<EditorElement> elements, String elementId) {
        if (!StringUtils.hasText(elementId)) {
            return null;
        }
        return elements.stream()
                .filter(element -> element.id().equals(elementId))
                .findFirst()
                .orElse(null);
    }

    private void replaceElement(List<EditorElement> elements, EditorElement replacement) {
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).id().equals(replacement.id())) {
                elements.set(i, replacement);
                return;
            }
        }
    }

    private PictureEditorDocumentResponse toResponse(PictureEditorDocument document, List<EditorElement> elements) {
        return PictureEditorDocumentResponse.builder()
                .pictureId(document.getPictureId())
                .schemaVersion(DOCUMENT_SCHEMA_VERSION)
                .version(document.getVersion())
                .lastUpdatedByUserId(document.getLastUpdatedByUserId())
                .updatedAt(document.getUpdatedAt())
                .elements(elements.stream().map(this::toElementResponse).toList())
                .build();
    }

    private PictureDocumentElementResponse toElementResponse(EditorElement element) {
        return PictureDocumentElementResponse.builder()
                .id(element.id())
                .type(element.type())
                .x(element.x())
                .y(element.y())
                .width(element.width())
                .height(element.height())
                .text(element.text())
                .build();
    }

    private List<EditorElement> deserialize(String documentContent) {
        try {
            return objectMapper.readValue(
                    StringUtils.hasText(documentContent) ? documentContent : "[]",
                    ELEMENT_LIST_TYPE
            );
        } catch (JsonProcessingException ex) {
            throw new ApiException(ApiErrorCode.SERVER_ERROR, "failed to read picture document");
        }
    }

    private String serialize(List<EditorElement> elements) {
        try {
            return objectMapper.writeValueAsString(elements);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ApiErrorCode.SERVER_ERROR, "failed to save picture document");
        }
    }

    private Double requiredNumber(Double value, String fieldName) {
        if (value == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "element type is required");
        }
        return type.trim().toLowerCase();
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "text content is required");
        }
        return text.trim();
    }

    private void verifyPictureExists(UUID pictureId) {
        if (pictureId == null || !pictureAssetRepository.existsById(pictureId)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "picture not found");
        }
    }

    public record AppliedOperation(
            long version,
            Instant updatedAt,
            PictureDocumentElementResponse element
    ) {
    }

    public record EditorElement(
            String id,
            String type,
            Double x,
            Double y,
            Double width,
            Double height,
            String text
    ) {
        EditorElement applyUpdate(PictureDocumentOperationPayload payload) {
            return new EditorElement(
                    id,
                    type,
                    payload.getX() != null ? payload.getX() : x,
                    payload.getY() != null ? payload.getY() : y,
                    payload.getWidth() != null ? payload.getWidth() : width,
                    payload.getHeight() != null ? payload.getHeight() : height,
                    "text".equals(type)
                            ? (payload.getText() != null ? payload.getText().trim() : text)
                            : null
            );
        }
    }
}
