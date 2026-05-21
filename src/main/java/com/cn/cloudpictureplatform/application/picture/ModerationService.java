package com.cn.cloudpictureplatform.application.picture;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.audit.ModerationRecord;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.ai.AiModerationRecord;
import com.cn.cloudpictureplatform.infrastructure.persistence.AiModerationRecordRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.ModerationRecordRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.application.shared.dto.AdminPictureSummary;
import com.cn.cloudpictureplatform.application.shared.dto.ModerationRecordResponse;
import com.cn.cloudpictureplatform.application.shared.dto.PictureResponse;
import com.cn.cloudpictureplatform.common.web.PageRequestFactory;

@Service
@Transactional(readOnly = true)
public class ModerationService {
    private final PictureAssetRepository pictureAssetRepository;
    private final ModerationRecordRepository moderationRecordRepository;
    private final AiModerationRecordRepository aiModerationRecordRepository;
    private final AppUserRepository appUserRepository;
    private final com.cn.cloudpictureplatform.application.search.SearchIndexService searchIndexService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public ModerationService(
            PictureAssetRepository pictureAssetRepository,
            ModerationRecordRepository moderationRecordRepository,
            AiModerationRecordRepository aiModerationRecordRepository,
            AppUserRepository appUserRepository,
            com.cn.cloudpictureplatform.application.search.SearchIndexService searchIndexService,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.pictureAssetRepository = pictureAssetRepository;
        this.moderationRecordRepository = moderationRecordRepository;
        this.aiModerationRecordRepository = aiModerationRecordRepository;
        this.appUserRepository = appUserRepository;
        this.searchIndexService = searchIndexService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicGallery", "pictureSearch", "adminPending", "moderationHistory", "pictureRecommendations"}, allEntries = true)
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
        eventPublisher.publishEvent(new PictureReviewedEvent(
                saved.getId(), saved.getName(), saved.getOwnerId(),
                status == ReviewStatus.APPROVED, record.getReason()
        ));

        return toResponse(saved);
    }

    @Transactional
    public void autoApprove(UUID pictureId, String provider) {
        PictureAsset asset = pictureAssetRepository.findById(pictureId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "picture not found"));
        asset.setReviewStatus(ReviewStatus.AUTO_APPROVED);
        asset = pictureAssetRepository.save(asset);
        searchIndexService.enqueuePicture(pictureId);
        eventPublisher.publishEvent(new PictureReviewedEvent(
                pictureId, asset.getName(), asset.getOwnerId(), true, "auto-approved by " + provider));
    }

    @Transactional
    public void autoReject(UUID pictureId, String reason, String provider) {
        PictureAsset asset = pictureAssetRepository.findById(pictureId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "picture not found"));
        asset.setReviewStatus(ReviewStatus.AUTO_REJECTED);
        asset = pictureAssetRepository.save(asset);
        searchIndexService.enqueuePicture(pictureId);
        eventPublisher.publishEvent(new PictureReviewedEvent(
                pictureId, asset.getName(), asset.getOwnerId(), false, reason));
    }

    @Transactional
    public void saveAiModerationResult(UUID pictureId, String provider, String modelVersion,
                                        boolean isSafe, double confidence,
                                        List<String> violationCategories, String rawResponse,
                                        int processingMs) {
        aiModerationRecordRepository.save(AiModerationRecord.builder()
                .pictureId(pictureId).provider(provider).modelVersion(modelVersion)
                .isSafe(isSafe).confidence(confidence)
                .violationCategories(violationCategories != null
                        ? String.join(",", violationCategories) : null)
                .rawResponse(rawResponse).processingMs(processingMs).build());
    }

    @Cacheable(cacheNames = "adminPending", key = "{ 'v1', #page, #size }")
    public PageResponse<AdminPictureSummary> listPending(int page, int size) {
        var pageable = PageRequestFactory.ofDescending(page, size, "createdAt");
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
                            .id(asset.getId()).name(asset.getName()).url(asset.getUrl())
                            .visibility(asset.getVisibility()).reviewStatus(asset.getReviewStatus())
                            .sizeBytes(asset.getSizeBytes()).width(asset.getWidth()).height(asset.getHeight())
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
        return new PageResponse<>(items, result.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize());
    }

    public AdminPictureSummary getAdminPicture(UUID pictureId) {
        PictureAsset asset = pictureAssetRepository.findById(pictureId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "picture not found"));
        Map<UUID, ModerationRecord> lastReviewMap = buildLastReviewMap(List.of(asset.getId()));
        ModerationRecord lastReview = lastReviewMap.get(asset.getId());
        List<UUID> userIds = new ArrayList<>();
        userIds.add(asset.getOwnerId());
        if (lastReview != null) userIds.add(lastReview.getReviewerId());
        Map<UUID, AppUser> userMap = buildUserMap(userIds);
        AppUser owner = userMap.get(asset.getOwnerId());
        AppUser reviewer = lastReview == null ? null : userMap.get(lastReview.getReviewerId());
        return AdminPictureSummary.builder()
                .id(asset.getId()).name(asset.getName()).url(asset.getUrl())
                .visibility(asset.getVisibility()).reviewStatus(asset.getReviewStatus())
                .sizeBytes(asset.getSizeBytes()).width(asset.getWidth()).height(asset.getHeight())
                .ownerId(asset.getOwnerId())
                .ownerUsername(owner == null ? null : owner.getUsername())
                .ownerDisplayName(owner == null ? null : owner.getDisplayName())
                .lastReviewerId(lastReview == null ? null : lastReview.getReviewerId())
                .lastReviewerUsername(reviewer == null ? null : reviewer.getUsername())
                .lastReviewerDisplayName(reviewer == null ? null : reviewer.getDisplayName())
                .lastReviewedAt(lastReview == null ? null : lastReview.getReviewedAt())
                .build();
    }

    @Cacheable(cacheNames = "moderationHistory",
            key = "{ 'v1', #pictureId, #page, #size, #reviewerId, #fromStatus, #toStatus, #reviewedAfter,"
                    + "#reviewedBefore, #sortBy, #sortDir }")
    public PageResponse<ModerationRecordResponse> listModerationHistory(
            UUID pictureId, int page, int size, UUID reviewerId,
            ReviewStatus fromStatus, ReviewStatus toStatus,
            Instant reviewedAfter, Instant reviewedBefore, String sortBy, String sortDir
    ) {
        int pageIndex = Math.max(0, page);
        int pageSize = Math.clamp(size, 1, 100);
        Sort sort = resolveSort(sortBy, sortDir);
        var pageable = PageRequestFactory.of(pageIndex, pageSize, sort);
        Specification<ModerationRecord> spec = buildModerationSpec(pictureId, reviewerId, fromStatus, toStatus, reviewedAfter, reviewedBefore);
        var result = moderationRecordRepository.findAll(spec, pageable);
        List<ModerationRecordResponse> items = toModerationResponses(result.getContent());
        return new PageResponse<>(items, result.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize());
    }

    public List<ModerationRecordResponse> exportModerationHistory(
            UUID pictureId, UUID reviewerId, ReviewStatus fromStatus, ReviewStatus toStatus,
            Instant reviewedAfter, Instant reviewedBefore, String sortBy, String sortDir, int limit
    ) {
        Sort sort = resolveSort(sortBy, sortDir);
        var pageable = PageRequestFactory.forExport(0, Math.clamp(limit, 1, 10000));
        Specification<ModerationRecord> spec = buildModerationSpec(pictureId, reviewerId, fromStatus, toStatus, reviewedAfter, reviewedBefore);
        var result = moderationRecordRepository.findAll(spec, pageable);
        return toModerationResponses(result.getContent());
    }

    private PictureResponse toResponse(PictureAsset asset) {
        return PictureResponse.builder()
                .id(asset.getId()).name(asset.getName()).url(asset.getUrl())
                .visibility(asset.getVisibility()).reviewStatus(asset.getReviewStatus())
                .sizeBytes(asset.getSizeBytes()).width(asset.getWidth()).height(asset.getHeight())
                .contentType(asset.getContentType())
                .build();
    }

    private Map<UUID, AppUser> buildUserMap(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        return appUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, user -> user));
    }

    private Map<UUID, ModerationRecord> buildLastReviewMap(List<UUID> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) return Map.of();
        List<ModerationRecord> records = moderationRecordRepository.findByPictureIdInOrderByReviewedAtDesc(pictureIds);
        Map<UUID, ModerationRecord> lastReviewMap = new HashMap<>();
        for (ModerationRecord record : records) {
            lastReviewMap.putIfAbsent(record.getPictureId(), record);
        }
        return lastReviewMap;
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        String resolvedSortBy = StringUtils.hasText(sortBy) ? sortBy : "reviewedAt";
        Set<String> allowedFields = Set.of("reviewedAt", "createdAt", "updatedAt");
        if (!allowedFields.contains(resolvedSortBy)) throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid sort field");
        Sort.Direction direction = Sort.Direction.DESC;
        if (StringUtils.hasText(sortDir)) {
            if ("asc".equalsIgnoreCase(sortDir)) direction = Sort.Direction.ASC;
            else if (!"desc".equalsIgnoreCase(sortDir)) throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid sort direction");
        }
        return Sort.by(direction, resolvedSortBy);
    }

    private Specification<ModerationRecord> buildModerationSpec(
            UUID pictureId, UUID reviewerId, ReviewStatus fromStatus, ReviewStatus toStatus,
            Instant reviewedAfter, Instant reviewedBefore
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (pictureId != null) predicates.add(builder.equal(root.get("pictureId"), pictureId));
            if (reviewerId != null) predicates.add(builder.equal(root.get("reviewerId"), reviewerId));
            if (fromStatus != null) predicates.add(builder.equal(root.get("fromStatus"), fromStatus));
            if (toStatus != null) predicates.add(builder.equal(root.get("toStatus"), toStatus));
            if (reviewedAfter != null) predicates.add(builder.greaterThanOrEqualTo(root.get("reviewedAt"), reviewedAfter));
            if (reviewedBefore != null) predicates.add(builder.lessThanOrEqualTo(root.get("reviewedAt"), reviewedBefore));
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
                            record.getId(), record.getPictureId(), record.getReviewerId(),
                            reviewer == null ? null : reviewer.getUsername(),
                            reviewer == null ? null : reviewer.getDisplayName(),
                            record.getFromStatus(), record.getToStatus(), record.getReason(), record.getReviewedAt()
                    );
                })
                .toList();
    }
}
