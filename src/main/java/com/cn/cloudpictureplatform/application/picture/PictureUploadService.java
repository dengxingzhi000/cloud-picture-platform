package com.cn.cloudpictureplatform.application.picture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.application.shared.dto.PictureResponse;
import com.cn.cloudpictureplatform.application.space.SpacePermissionValidator;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.storage.StorageResult;
import com.cn.cloudpictureplatform.domain.storage.StorageService;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;

@Service
public class PictureUploadService {
    private final StorageService storageService;
    private final PictureAssetRepository pictureAssetRepository;
    private final SpaceRepository spaceRepository;
    private final SpacePermissionValidator spacePermissionValidator;
    private final com.cn.cloudpictureplatform.application.search.SearchIndexService searchIndexService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public PictureUploadService(
            StorageService storageService,
            PictureAssetRepository pictureAssetRepository,
            SpaceRepository spaceRepository,
            SpacePermissionValidator spacePermissionValidator,
            com.cn.cloudpictureplatform.application.search.SearchIndexService searchIndexService,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.storageService = storageService;
        this.pictureAssetRepository = pictureAssetRepository;
        this.spaceRepository = spaceRepository;
        this.spacePermissionValidator = spacePermissionValidator;
        this.searchIndexService = searchIndexService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PictureResponse upload(UUID ownerId, MultipartFile file, Visibility visibility, String name, UUID spaceId) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "file is empty");
        }
        Space space = spacePermissionValidator.resolveAndValidateSpace(ownerId, spaceId);

        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "upload";
        String resolvedName = StringUtils.hasText(name) ? name : originalFilename;
        String key = buildStorageKey(ownerId, originalFilename);

        StorageResult storageResult = storageService.store(file, key);
        String checksum = computeChecksum(file);
        Integer width = null;
        Integer height = null;
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image != null) {
                width = image.getWidth();
                height = image.getHeight();
            }
        } catch (IOException ignored) {
        }

        Visibility resolvedVisibility = visibility == null ? Visibility.PRIVATE : visibility;
        ReviewStatus reviewStatus = resolvedVisibility == Visibility.PUBLIC
                ? ReviewStatus.PENDING
                : ReviewStatus.APPROVED;

        PictureAsset asset = PictureAsset.builder()
                .ownerId(ownerId)
                .spaceId(space.getId())
                .visibility(resolvedVisibility)
                .reviewStatus(reviewStatus)
                .name(resolvedName)
                .originalFilename(originalFilename)
                .contentType(storageResult.getContentType())
                .sizeBytes(storageResult.getSizeBytes())
                .checksum(checksum)
                .storageKey(storageResult.getKey())
                .url(storageResult.getUrl())
                .width(width)
                .height(height)
                .build();
        PictureAsset saved = pictureAssetRepository.save(asset);

        spaceRepository.incrementUsedBytes(space.getId(), storageResult.getSizeBytes());

        searchIndexService.enqueuePicture(saved.getId());
        eventPublisher.publishEvent(new PictureUploadedEvent(
                saved.getId(), saved.getName(), ownerId, space.getId(),
                space.getTeamId(), resolvedVisibility == Visibility.PUBLIC
        ));

        return toResponse(saved);
    }

    private String buildStorageKey(UUID ownerId, String originalFilename) {
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > -1 && dotIndex < originalFilename.length() - 1) {
            extension = originalFilename.substring(dotIndex);
        }
        return "pictures/" + ownerId + "/" + UUID.randomUUID() + extension;
    }

    private String computeChecksum(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new ApiException(ApiErrorCode.SERVER_ERROR, "failed to read file");
        }
    }

    private PictureResponse toResponse(PictureAsset asset) {
        return PictureResponse.builder()
                .id(asset.getId())
                .name(asset.getName())
                .url(asset.getUrl())
                .visibility(asset.getVisibility())
                .reviewStatus(asset.getReviewStatus())
                .sizeBytes(asset.getSizeBytes())
                .width(asset.getWidth())
                .height(asset.getHeight())
                .contentType(asset.getContentType())
                .build();
    }
}
