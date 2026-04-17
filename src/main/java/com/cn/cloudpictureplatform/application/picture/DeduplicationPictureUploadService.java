package com.cn.cloudpictureplatform.application.picture;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.storage.FileContent;
import com.cn.cloudpictureplatform.domain.storage.FileDeduplicationService;
import com.cn.cloudpictureplatform.domain.storage.ImageHashResult;
import com.cn.cloudpictureplatform.domain.storage.StorageResult;
import com.cn.cloudpictureplatform.domain.storage.StorageService;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.application.search.SearchIndexService;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureResponse;
import com.cn.cloudpictureplatform.websocket.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 支持去重的图片上传服务
 * 类似百度网盘秒传机制：检测重复文件，共用存储资源
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationPictureUploadService {

    private final StorageService storageService;
    private final FileDeduplicationService fileDeduplicationService;
    private final PictureAssetRepository pictureAssetRepository;
    private final SpaceRepository spaceRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AppUserRepository appUserRepository;
    private final PictureResponseConverter responseConverter;
    private final SearchIndexService searchIndexService;
    private final NotificationPublisher notificationPublisher;

    /**
     * 上传图片（支持去重）
     * 如果文件已存在，则复用已有存储，不重复上传
     *
     * @param ownerId    上传者ID
     * @param file       文件
     * @param visibility 可见性
     * @param name       文件名
     * @param spaceId    空间ID
     * @return 上传结果
     */
    @Transactional
    @CacheEvict(cacheNames = {"publicGallery", "pictureSearch", "adminPending", "pictureRecommendations"}, allEntries = true)
    public PictureResponse uploadWithDeduplication(
            UUID ownerId,
            MultipartFile file,
            Visibility visibility,
            String name,
            UUID spaceId
    ) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "file is empty");
        }

        // 1. 校验空间权限
        Space space = resolveSpace(ownerId, spaceId);

        // 2. 计算文件哈希（用于去重检测）
        ImageHashResult hashResult = computeHashes(file);

        // 3. 尝试查找已存在的相同文件
        Optional<FileContent> existingFile = fileDeduplicationService.findExactDuplicate(hashResult.getSha256Hash());

        FileContent fileContent;
        boolean isDuplicate = false;

        if (existingFile.isPresent()) {
            // 3a. 文件已存在，复用已有存储
            fileContent = existingFile.get();
            fileDeduplicationService.incrementRefCount(fileContent.getId());
            isDuplicate = true;
            log.info("Duplicate file detected, reusing existing storage: sha256={}, fileContentId={}",
                    hashResult.getSha256Hash(), fileContent.getId());
        } else {
            // 3b. 新文件，存储到存储服务
            String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                    ? file.getOriginalFilename()
                    : "upload";
            String key = buildStorageKey(ownerId, originalFilename);
            StorageResult storageResult = storageService.store(file, key);

            // 创建文件内容记录
            fileContent = fileDeduplicationService.createFileContent(
                    hashResult,
                    storageResult.getKey(),
                    storageResult.getUrl(),
                    ownerId,
                    originalFilename
            );
        }

        // 4. 创建图片资产记录（引用 file_content）
        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "upload";
        String resolvedName = StringUtils.hasText(name) ? name : originalFilename;
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
                .contentType(fileContent.getContentType())
                .sizeBytes(fileContent.getSizeBytes())
                .checksum(hashResult.getSha256Hash())  // 使用 SHA-256 作为 checksum
                .storageKey(fileContent.getStorageKey())
                .url(fileContent.getUrl())
                .fileContentId(fileContent.getId())  // 关联到 file_content
                .width(fileContent.getWidth())
                .height(fileContent.getHeight())
                .build();

        PictureAsset saved = pictureAssetRepository.save(asset);

        // 5. 更新空间使用量（即使是重复文件，也计算在用户的空间使用量中）
        space.setUsedBytes(space.getUsedBytes() + fileContent.getSizeBytes());
        spaceRepository.save(space);
        searchIndexService.enqueuePicture(saved.getId());
        notifyUploadRelatedParties(saved, space, ownerId);

        log.info("Picture uploaded with deduplication: pictureId={}, fileContentId={}, isDuplicate={}",
                saved.getId(), fileContent.getId(), isDuplicate);

        return responseConverter.toResponse(saved);
    }

    /**
     * 预检测上传 - 检查文件是否已存在（秒传接口）
     *
     * @param sha256Hash 文件的 SHA-256 哈希
     * @return 如果存在，返回已有的文件信息；否则返回空
     */
    public Optional<PictureAsset> checkDuplicate(UUID ownerId, String sha256Hash) {
        if (!StringUtils.hasText(sha256Hash)) {
            return Optional.empty();
        }
        return pictureAssetRepository.findByOwnerIdAndChecksum(ownerId, sha256Hash, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    // ============== 私有辅助方法 ==============

    private Space resolveSpace(UUID ownerId, UUID spaceId) {
        if (spaceId == null) {
            return spaceRepository.findFirstByOwnerIdAndType(ownerId, SpaceType.PERSONAL)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "space not found"));
        } else {
            Space space = spaceRepository.findById(spaceId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "space not found"));
            if (space.getType() != SpaceType.TEAM) {
                throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid target space");
            }
            TeamMember member = teamMemberRepository.findByTeamIdAndUserId(space.getTeamId(), ownerId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.FORBIDDEN, "not a member of this team"));
            if (member.getStatus() != TeamMemberStatus.ACTIVE) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "not an active team member");
            }
            return space;
        }
    }

    private ImageHashResult computeHashes(MultipartFile file) {
        try {
            return fileDeduplicationService.computeHashes(file);
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "failed to compute file hash: " + e.getMessage());
        }
    }

    private String buildStorageKey(UUID ownerId, String filename) {
        String extension = "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            extension = filename.substring(lastDot);
        }
        return String.format("users/%s/pictures/%s%s", ownerId, UUID.randomUUID(), extension);
    }

    private void notifyUploadRelatedParties(PictureAsset asset, Space space, UUID ownerId) {
        AppUser owner = appUserRepository.findById(ownerId).orElse(null);
        String ownerUsername = owner == null ? null : owner.getUsername();
        notificationPublisher.notifyUploadCompleted(ownerUsername, asset.getId(), asset.getName());

        if (asset.getVisibility() == Visibility.PUBLIC) {
            notificationPublisher.notifyAdminNewUpload(
                    asset.getId(),
                    asset.getName(),
                    ownerUsername == null ? "unknown" : ownerUsername
            );
        }

        if (space.getType() == SpaceType.TEAM && space.getTeamId() != null) {
            Collection<String> usernames = teamMemberRepository.findByTeamIdAndStatus(
                            space.getTeamId(),
                            TeamMemberStatus.ACTIVE
                    ).stream()
                    .map(TeamMember::getUserId)
                    .filter(userId -> !userId.equals(ownerId))
                    .map(userId -> appUserRepository.findById(userId).orElse(null))
                    .filter(Objects::nonNull)
                    .map(AppUser::getUsername)
                    .filter(StringUtils::hasText)
                    .toList();
            notificationPublisher.notifyTeamPictureUploaded(
                    usernames,
                    asset.getId(),
                    asset.getName(),
                    ownerUsername == null ? "unknown" : ownerUsername
            );
        }
    }
}
