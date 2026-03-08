package com.cn.cloudpictureplatform.application.picture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import javax.imageio.ImageIO;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.audit.ModerationRecord;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.PictureTag;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Tag;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.search.PictureSearchDocument;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserRole;
import com.cn.cloudpictureplatform.domain.storage.StorageResult;
import com.cn.cloudpictureplatform.domain.storage.StorageService;
import com.cn.cloudpictureplatform.application.search.SearchIndexService;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.ModerationRecordRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureTagRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TagRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import com.cn.cloudpictureplatform.interfaces.admin.dto.AdminPictureSummary;
import com.cn.cloudpictureplatform.interfaces.admin.dto.ModerationRecordResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureTagCreateRequest;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureTagItemRequest;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureTagResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureSummary;

@Service
public class PictureService {
    private final StorageService storageService;
    private final PictureAssetRepository pictureAssetRepository;
    private final SpaceRepository spaceRepository;
    private final ModerationRecordRepository moderationRecordRepository;
    private final AppUserRepository appUserRepository;
    private final PictureTagRepository pictureTagRepository;
    private final TagRepository tagRepository;
    private final SearchIndexService searchIndexService;
    private final TeamMemberRepository teamMemberRepository;

    public PictureService(
            StorageService storageService,
            PictureAssetRepository pictureAssetRepository,
            SpaceRepository spaceRepository,
            ModerationRecordRepository moderationRecordRepository,
            AppUserRepository appUserRepository,
            PictureTagRepository pictureTagRepository,
            TagRepository tagRepository,
            SearchIndexService searchIndexService,
            TeamMemberRepository teamMemberRepository
    ) {
        this.storageService = storageService;
        this.pictureAssetRepository = pictureAssetRepository;
        this.spaceRepository = spaceRepository;
        this.moderationRecordRepository = moderationRecordRepository;
        this.appUserRepository = appUserRepository;
        this.pictureTagRepository = pictureTagRepository;
        this.tagRepository = tagRepository;
        this.searchIndexService = searchIndexService;
        this.teamMemberRepository = teamMemberRepository;
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicGallery", "pictureSearch", "adminPending"}, allEntries = true)
    public PictureResponse upload(UUID ownerId, MultipartFile file, Visibility visibility, String name) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "file is empty");
        }
        Space space = spaceRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "space not found"));

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
            // Skip dimensions when image parsing fails.
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

        space.setUsedBytes(space.getUsedBytes() + storageResult.getSizeBytes());
        spaceRepository.save(space);

        searchIndexService.enqueuePicture(saved.getId());

        return toResponse(saved);
    }

    public PageResponse<PictureSummary> listPublic(int page, int size) {
        return listPublic(page, size, null, null, null, null);
    }

    @Cacheable(
            cacheNames = "publicGallery",
            key = "T(java.util.Arrays).asList(#page,#size,#keyword,#minSizeBytes,#maxSizeBytes,#orientation)"
    )
    public PageResponse<PictureSummary> listPublic(
            int page,
            int size,
            String keyword,
            Long minSizeBytes,
            Long maxSizeBytes,
            String orientation
    ) {
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), 100);
        var pageable = PageRequest.of(pageIndex, pageSize, Sort.by("createdAt").descending());
        Specification<PictureAsset> spec = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("visibility"), Visibility.PUBLIC));
            predicates.add(builder.equal(root.get("reviewStatus"), ReviewStatus.APPROVED));
            if (StringUtils.hasText(keyword)) {
                String likeValue = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(builder.like(builder.lower(root.get("name")), likeValue));
            }
            if (minSizeBytes != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("sizeBytes"), minSizeBytes));
            }
            if (maxSizeBytes != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("sizeBytes"), maxSizeBytes));
            }
            if (StringUtils.hasText(orientation)) {
                String value = orientation.trim().toUpperCase();
                predicates.add(builder.isNotNull(root.get("width")));
                predicates.add(builder.isNotNull(root.get("height")));
                switch (value) {
                    case "LANDSCAPE" ->
                            predicates.add(builder.greaterThan(root.get("width"), root.get("height")));
                    case "PORTRAIT" ->
                            predicates.add(builder.greaterThan(root.get("height"), root.get("width")));
                    case "SQUARE" ->
                            predicates.add(builder.equal(root.get("width"), root.get("height")));
                    default -> throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid orientation");
                }
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        var result = pictureAssetRepository.findAll(spec, pageable);
        List<PictureSummary> items = result.getContent().stream()
                .map(asset -> new PictureSummary(
                        asset.getId(),
                        asset.getName(),
                        asset.getUrl(),
                        asset.getVisibility(),
                        asset.getSizeBytes(),
                        asset.getWidth(),
                        asset.getHeight()
                ))
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), pageIndex, pageSize);
    }

    private Subquery<UUID> buildSearchSubquery(
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Root<PictureAsset> root,
            String likeValue
    ) {
        Subquery<UUID> subquery = query.subquery(UUID.class);
        var docRoot = subquery.from(PictureSearchDocument.class);
        subquery.select(docRoot.get("pictureId"))
                .where(
                        builder.equal(docRoot.get("pictureId"), root.get("id")),
                        builder.like(builder.lower(docRoot.get("content")), likeValue)
                );
        return subquery;
    }

    @Cacheable(
            cacheNames = "pictureSearch",
            key = "T(java.util.Arrays).asList(#page,#size,#keyword,#ownerId,#spaceId,#visibility,#reviewStatus,"
                    + "#minSizeBytes,#maxSizeBytes,#createdAfter,#createdBefore,#orientation,#tag,#tagId,"
                    + "#sortBy,#sortDir,#requesterId,#requesterRole)"
    )
    public PageResponse<PictureSummary> searchPictures(
            int page,
            int size,
            String keyword,
            UUID ownerId,
            UUID spaceId,
            Visibility visibility,
            ReviewStatus reviewStatus,
            Long minSizeBytes,
            Long maxSizeBytes,
            Instant createdAfter,
            Instant createdBefore,
            String orientation,
            String tag,
            UUID tagId,
            String sortBy,
            String sortDir,
            UUID requesterId,
            UserRole requesterRole
    ) {
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), 100);
        Sort sort = resolvePictureSort(sortBy, sortDir);
        var pageable = PageRequest.of(pageIndex, pageSize, sort);
        boolean isAdmin = requesterRole == UserRole.ADMIN;
        List<UUID> teamSpaceIds = resolveTeamSpaceIds(isAdmin, requesterId);
        Specification<PictureAsset> spec = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!isAdmin) {
                if (requesterId == null) {
                    predicates.add(builder.disjunction());
                } else {
                    Predicate ownerPredicate = builder.equal(root.get("ownerId"), requesterId);
                    if (teamSpaceIds.isEmpty()) {
                        predicates.add(ownerPredicate);
                    } else {
                        Predicate spacePredicate = root.get("spaceId").in(teamSpaceIds);
                        Predicate nonPrivate = builder.notEqual(root.get("visibility"), Visibility.PRIVATE);
                        predicates.add(builder.or(ownerPredicate, builder.and(spacePredicate, nonPrivate)));
                    }
                }
            }
            if (ownerId != null) {
                predicates.add(builder.equal(root.get("ownerId"), ownerId));
            }
            if (spaceId != null) {
                predicates.add(builder.equal(root.get("spaceId"), spaceId));
            }
            if (visibility != null) {
                predicates.add(builder.equal(root.get("visibility"), visibility));
            }
            if (reviewStatus != null) {
                predicates.add(builder.equal(root.get("reviewStatus"), reviewStatus));
            }
            if (StringUtils.hasText(keyword)) {
                String likeValue = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), likeValue),
                        builder.exists(buildSearchSubquery(query, builder, root, likeValue))
                ));
            }
            if (minSizeBytes != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("sizeBytes"), minSizeBytes));
            }
            if (maxSizeBytes != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("sizeBytes"), maxSizeBytes));
            }
            if (createdAfter != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
            }
            if (createdBefore != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
            }
            if (StringUtils.hasText(orientation)) {
                String value = orientation.trim().toUpperCase();
                predicates.add(builder.isNotNull(root.get("width")));
                predicates.add(builder.isNotNull(root.get("height")));
                switch (value) {
                    case "LANDSCAPE" ->
                            predicates.add(builder.greaterThan(root.get("width"), root.get("height")));
                    case "PORTRAIT" ->
                            predicates.add(builder.greaterThan(root.get("height"), root.get("width")));
                    case "SQUARE" ->
                            predicates.add(builder.equal(root.get("width"), root.get("height")));
                    default -> throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid orientation");
                }
            }
            if (tagId != null || StringUtils.hasText(tag)) {
                String normalizedTag = StringUtils.hasText(tag) ? tag.trim().toLowerCase() : null;
                Subquery<UUID> subquery = query.subquery(UUID.class);
                var tagRoot = subquery.from(PictureTag.class);
                List<Predicate> tagPredicates = new ArrayList<>();
                tagPredicates.add(builder.equal(tagRoot.get("pictureAssetId"), root.get("id")));
                if (tagId != null) {
                    tagPredicates.add(builder.equal(tagRoot.get("tagId"), tagId));
                }
                if (normalizedTag != null) {
                    tagPredicates.add(builder.equal(builder.lower(tagRoot.get("tagText")), normalizedTag));
                }
                subquery.select(tagRoot.get("pictureAssetId"))
                        .where(tagPredicates.toArray(Predicate[]::new));
                predicates.add(builder.exists(subquery));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        var result = pictureAssetRepository.findAll(spec, pageable);
        List<PictureSummary> items = result.getContent().stream()
                .map(asset -> new PictureSummary(
                        asset.getId(),
                        asset.getName(),
                        asset.getUrl(),
                        asset.getVisibility(),
                        asset.getSizeBytes(),
                        asset.getWidth(),
                        asset.getHeight()
                ))
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), pageIndex, pageSize);
    }

    private List<UUID> resolveTeamSpaceIds(boolean isAdmin, UUID requesterId) {
        if (isAdmin || requesterId == null) {
            return List.of();
        }
        List<UUID> teamIds = teamMemberRepository.findByUserIdAndStatus(
                requesterId,
                TeamMemberStatus.ACTIVE
        ).stream().map(TeamMember::getTeamId).toList();
        if (teamIds.isEmpty()) {
            return List.of();
        }
        return spaceRepository.findByTeamIdIn(teamIds).stream()
                .map(Space::getId)
                .toList();
    }

    @Cacheable(cacheNames = "adminPending", key = "T(java.util.Arrays).asList(#page,#size)")
    public PageResponse<AdminPictureSummary> listPending(int page, int size) {
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), 100);
        var pageable = PageRequest.of(pageIndex, pageSize, Sort.by("createdAt").descending());
        var result = pictureAssetRepository.findByReviewStatus(ReviewStatus.PENDING, pageable);
        List<PictureAsset> assets = result.getContent();
        List<UUID> ownerIds = assets.stream().map(PictureAsset::getOwnerId).distinct().toList();
        Map<UUID, ModerationRecord> lastReviewMap = buildLastReviewMap(
                assets.stream().map(PictureAsset::getId).toList()
        );
        List<UUID> reviewerIds = lastReviewMap.values().stream()
                .map(ModerationRecord::getReviewerId)
                .distinct()
                .toList();
        List<UUID> userIds = new ArrayList<>(ownerIds.size() + reviewerIds.size());
        userIds.addAll(ownerIds);
        userIds.addAll(reviewerIds);
        Map<UUID, AppUser> userMap = buildUserMap(userIds);
        List<AdminPictureSummary> items = assets.stream()
                .map(asset -> {
                    AppUser owner = userMap.get(asset.getOwnerId());
                    ModerationRecord lastReview = lastReviewMap.get(asset.getId());
                    AppUser reviewer = lastReview == null ? null : userMap.get(lastReview.getReviewerId());

                    return AdminPictureSummary.builder()
                            .id(asset.getId())
                            .name(asset.getName())
                            .url(asset.getUrl())
                            .visibility(asset.getVisibility())
                            .reviewStatus(asset.getReviewStatus())
                            .sizeBytes(asset.getSizeBytes())
                            .width(asset.getWidth())
                            .height(asset.getHeight())
                            .ownerId(asset.getOwnerId())
                            .ownerUsername(owner == null ? null : owner.getUsername())
                            .ownerDisplayName(owner == null ? null : owner.getDisplayName())
                            .lastReviewerId(lastReview == null ? null : lastReview.getReviewerId())
                            .lastReviewerUsername(reviewer == null ? null : reviewer.getUsername())
                            .lastReviewerDisplayName(reviewer == null ? null : reviewer.getDisplayName())
                            .lastReviewedAt(lastReview == null ? null : lastReview.getReviewedAt())
                            .build();
                })
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), pageIndex, pageSize);
    }

    public AdminPictureSummary getAdminPicture(UUID pictureId) {
        PictureAsset asset = pictureAssetRepository.findById(pictureId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "picture not found"));
        Map<UUID, ModerationRecord> lastReviewMap = buildLastReviewMap(List.of(asset.getId()));
        ModerationRecord lastReview = lastReviewMap.get(asset.getId());
        List<UUID> userIds = new ArrayList<>();
        userIds.add(asset.getOwnerId());
        if (lastReview != null) {
            userIds.add(lastReview.getReviewerId());
        }
        Map<UUID, AppUser> userMap = buildUserMap(userIds);
        AppUser owner = userMap.get(asset.getOwnerId());
        AppUser reviewer = lastReview == null ? null : userMap.get(lastReview.getReviewerId());
        return AdminPictureSummary.builder()
                .id(asset.getId())
                .name(asset.getName())
                .url(asset.getUrl())
                .visibility(asset.getVisibility())
                .reviewStatus(asset.getReviewStatus())
                .sizeBytes(asset.getSizeBytes())
                .width(asset.getWidth())
                .height(asset.getHeight())
                .ownerId(asset.getOwnerId())
                .ownerUsername(owner == null ? null : owner.getUsername())
                .ownerDisplayName(owner == null ? null : owner.getDisplayName())
                .lastReviewerId(lastReview == null ? null : lastReview.getReviewerId())
                .lastReviewerUsername(reviewer == null ? null : reviewer.getUsername())
                .lastReviewerDisplayName(reviewer == null ? null : reviewer.getDisplayName())
                .lastReviewedAt(lastReview == null ? null : lastReview.getReviewedAt())
                .build();
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicGallery", "pictureSearch", "adminPending", "moderationHistory"}, allEntries = true)
    public PictureResponse review(UUID pictureId, UUID reviewerId, ReviewStatus status, String reason) {
        if (status == null || status == ReviewStatus.PENDING) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid review status");
        }
        PictureAsset asset = pictureAssetRepository.findById(pictureId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "picture not found"));
        if (asset.getVisibility() != Visibility.PUBLIC) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "only public assets can be reviewed");
        }
        if (asset.getReviewStatus() == status) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "status already applied");
        }
        ReviewStatus fromStatus = asset.getReviewStatus();
        asset.setReviewStatus(status);
        PictureAsset saved = pictureAssetRepository.save(asset);

        ModerationRecord record = ModerationRecord.builder()
                .pictureId(saved.getId())
                .reviewerId(reviewerId)
                .fromStatus(fromStatus)
                .toStatus(status)
                .reason(StringUtils.hasText(reason) ? reason.trim() : null)
                .reviewedAt(Instant.now())
                .build();
        moderationRecordRepository.save(record);

        searchIndexService.enqueuePicture(saved.getId());

        return toResponse(saved);
    }

    public List<PictureTagResponse> listTags(UUID pictureId) {
        verifyPictureExists(pictureId);
        return pictureTagRepository.findByPictureAssetIdOrderByCreatedAtDesc(pictureId).stream()
                .map(this::toTagResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = "pictureSearch", allEntries = true)
    public List<PictureTagResponse> addTags(UUID pictureId, PictureTagCreateRequest request) {
        if (request == null || request.getTags() == null || request.getTags().isEmpty()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "tags are required");
        }
        verifyPictureExists(pictureId);
        String provider = StringUtils.hasText(request.getProvider()) ? request.getProvider().trim() : null;
        boolean autoGenerated = Boolean.TRUE.equals(request.getAutoGenerated());
        List<PictureTag> existingTags = pictureTagRepository.findByPictureAssetId(pictureId);
        Set<String> existingKeys = existingTags.stream()
                .map(tag -> buildTagKey(tag.getTagText(), tag.getProvider(), tag.getIsAutoGenerated()))
                .collect(Collectors.toSet());

        List<PictureTag> toSave = new ArrayList<>();
        for (PictureTagItemRequest item : request.getTags()) {
            String tagText = item == null ? null : item.getText();
            if (!StringUtils.hasText(tagText)) {
                throw new ApiException(ApiErrorCode.BAD_REQUEST, "tag text is required");
            }
            String trimmed = tagText.trim();
            Tag catalog = findOrCreateTag(trimmed);
            String key = buildTagKey(trimmed, provider, autoGenerated);
            if (existingKeys.contains(key)) {
                continue;
            }
            PictureTag tag = PictureTag.builder()
                    .pictureAssetId(pictureId)
                    .tagId(catalog == null ? null : catalog.getId())
                    .tagText(trimmed)
                    .confidenceScore(item.getConfidenceScore())
                    .provider(provider)
                    .isAutoGenerated(autoGenerated)
                    .build();
            toSave.add(tag);
            existingKeys.add(key);
        }

        if (!toSave.isEmpty()) {
            pictureTagRepository.saveAll(toSave);
            searchIndexService.enqueuePicture(pictureId);
        }
        return listTags(pictureId);
    }

    @Transactional
    @CacheEvict(cacheNames = "pictureSearch", allEntries = true)
    public void removeTag(UUID pictureId, UUID tagId) {
        verifyPictureExists(pictureId);
        PictureTag tag = pictureTagRepository.findById(tagId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "tag not found"));
        if (!pictureId.equals(tag.getPictureAssetId())) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "tag not found");
        }
        pictureTagRepository.delete(tag);
        searchIndexService.enqueuePicture(pictureId);
    }

    @Cacheable(
            cacheNames = "moderationHistory",
            key = "T(java.util.Arrays).asList(#pictureId,#page,#size,#reviewerId,#fromStatus,#toStatus,#reviewedAfter,"
                    + "#reviewedBefore,#sortBy,#sortDir)"
    )
    public PageResponse<ModerationRecordResponse> listModerationHistory(
            UUID pictureId,
            int page,
            int size,
            UUID reviewerId,
            ReviewStatus fromStatus,
            ReviewStatus toStatus,
            Instant reviewedAfter,
            Instant reviewedBefore,
            String sortBy,
            String sortDir
    ) {
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), 100);
        Sort sort = resolveSort(sortBy, sortDir);
        var pageable = PageRequest.of(pageIndex, pageSize, sort);
        Specification<ModerationRecord> spec = buildModerationSpec(
                pictureId,
                reviewerId,
                fromStatus,
                toStatus,
                reviewedAfter,
                reviewedBefore
        );
        var result = moderationRecordRepository.findAll(spec, pageable);
        List<ModerationRecordResponse> items = toModerationResponses(result.getContent());
        return new PageResponse<>(items, result.getTotalElements(), pageIndex, pageSize);
    }

    public List<ModerationRecordResponse> exportModerationHistory(
            UUID pictureId,
            UUID reviewerId,
            ReviewStatus fromStatus,
            ReviewStatus toStatus,
            Instant reviewedAfter,
            Instant reviewedBefore,
            String sortBy,
            String sortDir,
            int limit
    ) {
        int pageSize = Math.min(Math.max(1, limit), 10000);
        Sort sort = resolveSort(sortBy, sortDir);
        var pageable = PageRequest.of(0, pageSize, sort);
        Specification<ModerationRecord> spec = buildModerationSpec(
                pictureId,
                reviewerId,
                fromStatus,
                toStatus,
                reviewedAfter,
                reviewedBefore
        );
        var result = moderationRecordRepository.findAll(spec, pageable);
        return toModerationResponses(result.getContent());
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
            return DigestUtils.md5DigestAsHex(inputStream);
        } catch (IOException ex) {
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

    private PictureTagResponse toTagResponse(PictureTag tag) {
        return PictureTagResponse.builder()
                .id(tag.getId())
                .pictureAssetId(tag.getPictureAssetId())
                .tagId(tag.getTagId())
                .tagText(tag.getTagText())
                .confidenceScore(tag.getConfidenceScore())
                .provider(tag.getProvider())
                .autoGenerated(tag.getIsAutoGenerated())
                .createdAt(tag.getCreatedAt())
                .build();
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        String resolvedSortBy = StringUtils.hasText(sortBy) ? sortBy : "reviewedAt";
        Set<String> allowedFields = Set.of("reviewedAt", "createdAt", "updatedAt");
        if (!allowedFields.contains(resolvedSortBy)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid sort field");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (StringUtils.hasText(sortDir)) {
            if ("asc".equalsIgnoreCase(sortDir)) {
                direction = Sort.Direction.ASC;
            } else if (!"desc".equalsIgnoreCase(sortDir)) {
                throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid sort direction");
            }
        }
        return Sort.by(direction, resolvedSortBy);
    }

    private Sort resolvePictureSort(String sortBy, String sortDir) {
        String resolvedSortBy = StringUtils.hasText(sortBy) ? sortBy : "createdAt";
        Set<String> allowedFields = Set.of("createdAt", "updatedAt", "sizeBytes");
        if (!allowedFields.contains(resolvedSortBy)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid sort field");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (StringUtils.hasText(sortDir)) {
            if ("asc".equalsIgnoreCase(sortDir)) {
                direction = Sort.Direction.ASC;
            } else if (!"desc".equalsIgnoreCase(sortDir)) {
                throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid sort direction");
            }
        }
        return Sort.by(direction, resolvedSortBy);
    }

    private Specification<ModerationRecord> buildModerationSpec(
            UUID pictureId,
            UUID reviewerId,
            ReviewStatus fromStatus,
            ReviewStatus toStatus,
            Instant reviewedAfter,
            Instant reviewedBefore
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (pictureId != null) {
                predicates.add(builder.equal(root.get("pictureId"), pictureId));
            }
            if (reviewerId != null) {
                predicates.add(builder.equal(root.get("reviewerId"), reviewerId));
            }
            if (fromStatus != null) {
                predicates.add(builder.equal(root.get("fromStatus"), fromStatus));
            }
            if (toStatus != null) {
                predicates.add(builder.equal(root.get("toStatus"), toStatus));
            }
            if (reviewedAfter != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("reviewedAt"), reviewedAfter));
            }
            if (reviewedBefore != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("reviewedAt"), reviewedBefore));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<ModerationRecordResponse> toModerationResponses(List<ModerationRecord> records) {
        Map<UUID, AppUser> reviewerMap = buildUserMap(
                records.stream().map(ModerationRecord::getReviewerId).distinct().toList()
        );
        return records.stream()
                .map(record -> {
                    AppUser reviewer = reviewerMap.get(record.getReviewerId());
                    return new ModerationRecordResponse(
                            record.getId(),
                            record.getPictureId(),
                            record.getReviewerId(),
                            reviewer == null ? null : reviewer.getUsername(),
                            reviewer == null ? null : reviewer.getDisplayName(),
                            record.getFromStatus(),
                            record.getToStatus(),
                            record.getReason(),
                            record.getReviewedAt()
                    );
                })
                .toList();
    }

    private Map<UUID, AppUser> buildUserMap(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return appUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, user -> user));
    }

    private Map<UUID, ModerationRecord> buildLastReviewMap(List<UUID> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            return Map.of();
        }
        List<ModerationRecord> records = moderationRecordRepository
                .findByPictureIdInOrderByReviewedAtDesc(pictureIds);
        Map<UUID, ModerationRecord> lastReviewMap = new HashMap<>();
        for (ModerationRecord record : records) {
            lastReviewMap.putIfAbsent(record.getPictureId(), record);
        }
        return lastReviewMap;
    }

    private void verifyPictureExists(UUID pictureId) {
        if (pictureId == null || !pictureAssetRepository.existsById(pictureId)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "picture not found");
        }
    }

    private String buildTagKey(String tagText, String provider, boolean autoGenerated) {
        String normalizedText = tagText == null ? "" : tagText.trim().toLowerCase();
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase();
        return normalizedText + "|" + normalizedProvider + "|" + autoGenerated;
    }

    private Tag findOrCreateTag(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return tagRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Tag tag = new Tag();
                    tag.setName(name);
                    return tagRepository.save(tag);
                });
    }
}
