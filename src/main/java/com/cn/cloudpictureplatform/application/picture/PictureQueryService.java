package com.cn.cloudpictureplatform.application.picture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.PictureTag;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.team.Team;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureTagRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamRepository;
import com.cn.cloudpictureplatform.application.shared.dto.PictureDetailResponse;
import com.cn.cloudpictureplatform.application.shared.dto.PictureSummary;
import com.cn.cloudpictureplatform.application.shared.dto.PictureTagResponse;

@Service
@Transactional(readOnly = true)
public class PictureQueryService {

    private final PictureAssetRepository pictureAssetRepository;
    private final SpaceRepository spaceRepository;
    private final AppUserRepository appUserRepository;
    private final PictureTagRepository pictureTagRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;

    public PictureQueryService(
            PictureAssetRepository pictureAssetRepository,
            SpaceRepository spaceRepository,
            AppUserRepository appUserRepository,
            PictureTagRepository pictureTagRepository,
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository
    ) {
        this.pictureAssetRepository = pictureAssetRepository;
        this.spaceRepository = spaceRepository;
        this.appUserRepository = appUserRepository;
        this.pictureTagRepository = pictureTagRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
    }

    public PictureDetailResponse getPictureDetail(UUID pictureId, UUID requesterId, Set<String> requesterRoles) {
        PictureAsset asset = pictureAssetRepository.findById(pictureId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "picture not found"));
        Space space = spaceRepository.findById(asset.getSpaceId())
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "space not found"));
        boolean isAdmin = requesterRoles != null && requesterRoles.contains("ROLE_ADMIN");
        TeamMember activeTeamMember = resolveActiveTeamMember(space, requesterId);
        if (!canView(asset, space, requesterId, isAdmin, activeTeamMember)) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "insufficient permissions");
        }

        AppUser owner = appUserRepository.findById(asset.getOwnerId()).orElse(null);
        Team team = space.getTeamId() == null ? null : teamRepository.findById(space.getTeamId()).orElse(null);
        List<PictureTagResponse> tags = pictureTagRepository
                .findByPictureAssetId(pictureId)
                .stream()
                .map(tag -> PictureTagResponse.builder()
                        .id(tag.getId())
                        .pictureAssetId(tag.getPictureAssetId())
                        .tagId(tag.getTagId())
                        .tagText(tag.getTagText())
                        .confidenceScore(tag.getConfidenceScore())
                        .provider(tag.getProvider())
                        .autoGenerated(tag.getIsAutoGenerated())
                        .createdAt(tag.getCreatedAt())
                        .build()
                )
                .toList();

        boolean isOwner = requesterId != null && requesterId.equals(asset.getOwnerId());
        boolean canEdit = isAdmin || isOwner || activeTeamMember != null;
        boolean canManage = isAdmin || isOwner
                || (activeTeamMember != null
                && (activeTeamMember.getRole() == TeamRole.OWNER || activeTeamMember.getRole() == TeamRole.ADMIN));

        return PictureDetailResponse.builder()
                .id(asset.getId())
                .name(asset.getName())
                .originalFilename(asset.getOriginalFilename())
                .url(asset.getUrl())
                .contentType(asset.getContentType())
                .sizeBytes(asset.getSizeBytes())
                .checksum(asset.getChecksum())
                .width(asset.getWidth())
                .height(asset.getHeight())
                .visibility(asset.getVisibility())
                .reviewStatus(asset.getReviewStatus())
                .ownerId(asset.getOwnerId())
                .ownerUsername(owner == null ? null : owner.getUsername())
                .ownerDisplayName(owner == null ? null : owner.getDisplayName())
                .spaceId(space.getId())
                .spaceName(space.getName())
                .spaceType(space.getType())
                .teamId(space.getTeamId())
                .teamName(team == null ? null : team.getName())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .canEdit(canEdit)
                .canManage(canManage)
                .canJoinCollaboration(space.getType() == SpaceType.TEAM && canEdit)
                .tags(tags)
                .build();
    }

    public PageResponse<PictureSummary> listPublic(int page, int size) {
        return listPublic(page, size, null, null, null, null);
    }

    @Cacheable(cacheNames = "publicGallery",
            key = "{ 'v1', #page, #size, #keyword, #minSizeBytes, #maxSizeBytes, #orientation }")
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
        String normalizedKeyword = normalizeKeyword(keyword);
        Sort sort = resolveKeywordAwareSort(normalizedKeyword, null, null);
        var pageable = sort.isSorted()
                ? PageRequest.of(pageIndex, pageSize, sort)
                : PageRequest.of(pageIndex, pageSize);
        Specification<PictureAsset> spec = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("visibility"), Visibility.PUBLIC));
            predicates.add(builder.equal(root.get("reviewStatus"), ReviewStatus.APPROVED));
            if (StringUtils.hasText(normalizedKeyword)) {
                String likeValue = "%" + normalizedKeyword + "%";
                predicates.add(builder.like(builder.lower(root.get("name")), likeValue));
                applyKeywordOrdering(query, builder, root, normalizedKeyword, likeValue);
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

    @Cacheable(cacheNames = "pictureRecommendations",
            key = "{ 'v1', #page, #size, #requesterId }")
    public PageResponse<PictureSummary> recommendPublic(int page, int size, UUID requesterId) {
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), 100);

        LinkedHashMap<String, Integer> interestTagWeights = resolveInterestTagWeights(requesterId);

        if (interestTagWeights.isEmpty()) {
            return listPublic(pageIndex, pageSize);
        }

        List<UUID> candidateIds = pictureTagRepository.findRecommendedPictureCandidateIds(
                new ArrayList<>(interestTagWeights.keySet()),
                requesterId
        );

        if (candidateIds.isEmpty()) {
            return listPublic(pageIndex, pageSize);
        }

        Map<UUID, PictureAsset> assetMap = pictureAssetRepository.findAllById(candidateIds)
                .stream().collect(Collectors.toMap(PictureAsset::getId, a -> a));
        Map<UUID, Set<String>> tagMap = pictureTagRepository.findByPictureAssetIdIn(candidateIds).stream()
                .collect(Collectors.groupingBy(
                        PictureTag::getPictureAssetId,
                        Collectors.mapping(
                                tag -> normalizeTagText(tag.getTagText()),
                                Collectors.filtering(StringUtils::hasText, Collectors.toSet())
                        )
                ));
        List<PictureAsset> rankedAssets = candidateIds.stream()
                .map(assetMap::get)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingDouble((PictureAsset asset) ->
                                recommendationScore(asset, tagMap.getOrDefault(asset.getId(), Set.of()), interestTagWeights))
                        .reversed()
                        .thenComparing(PictureAsset::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int fromIndex = Math.min(pageIndex * pageSize, rankedAssets.size());
        int toIndex = Math.min(fromIndex + pageSize, rankedAssets.size());
        List<PictureSummary> items = rankedAssets.subList(fromIndex, toIndex).stream()
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

        return new PageResponse<>(items, rankedAssets.size(), pageIndex, pageSize);
    }

    @Cacheable(cacheNames = "pictureSearch",
            key = "{ 'v1', #page, #size, #keyword, #ownerId, #spaceId, #visibility, #reviewStatus,"
                    + "#minSizeBytes, #maxSizeBytes, #createdAfter, #createdBefore, #orientation, #tag, #tagId,"
                    + "#sortBy, #sortDir, #requesterId, #requesterRoles }")
    public PageResponse<PictureSummary> searchPictures(
            int page, int size, String keyword, UUID ownerId, UUID spaceId,
            Visibility visibility, ReviewStatus reviewStatus,
            Long minSizeBytes, Long maxSizeBytes,
            java.time.Instant createdAfter, java.time.Instant createdBefore,
            String orientation, String tag, UUID tagId,
            String sortBy, String sortDir, UUID requesterId, Set<String> requesterRoles
    ) {
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), 100);
        String normalizedKeyword = normalizeKeyword(keyword);
        Sort sort = resolveKeywordAwareSort(normalizedKeyword, sortBy, sortDir);
        var pageable = PageRequest.of(pageIndex, pageSize, sort);
        boolean isAdmin = requesterRoles != null && requesterRoles.contains("ROLE_ADMIN");
        List<UUID> teamSpaceIds = resolveTeamSpaceIds(isAdmin, requesterId);
        Specification<PictureAsset> spec = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!isAdmin) {
                Predicate publicApproved = builder.and(
                        builder.equal(root.get("visibility"), Visibility.PUBLIC),
                        builder.equal(root.get("reviewStatus"), ReviewStatus.APPROVED)
                );
                if (requesterId == null) {
                    predicates.add(publicApproved);
                } else {
                    Predicate ownerPredicate = builder.equal(root.get("ownerId"), requesterId);
                    if (teamSpaceIds.isEmpty()) {
                        predicates.add(builder.or(ownerPredicate, publicApproved));
                    } else {
                        Predicate spacePredicate = root.get("spaceId").in(teamSpaceIds);
                        Predicate nonPrivate = builder.notEqual(root.get("visibility"), Visibility.PRIVATE);
                        predicates.add(builder.or(ownerPredicate, builder.and(spacePredicate, nonPrivate), publicApproved));
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
            if (StringUtils.hasText(normalizedKeyword)) {
                String likeValue = "%" + normalizedKeyword + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), likeValue),
                        builder.like(builder.lower(root.get("originalFilename")), likeValue),
                        builder.exists(buildSearchSubquery(query, builder, root, likeValue))
                ));
                applyKeywordOrdering(query, builder, root, normalizedKeyword, likeValue);
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
                var tagRoot = subquery.from(com.cn.cloudpictureplatform.domain.picture.PictureTag.class);
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

    private LinkedHashMap<String, Integer> resolveInterestTagWeights(UUID requesterId) {
        var top20 = PageRequest.of(0, 20);
        if (requesterId != null) {
            List<String> userTags = pictureTagRepository.findTopTagTextsByOwnerId(requesterId, top20);
            if (!userTags.isEmpty()) {
                return rankInterestTags(userTags);
            }
        }
        return rankInterestTags(pictureTagRepository.findPopularTagTexts(top20));
    }

    private LinkedHashMap<String, Integer> rankInterestTags(List<String> tagTexts) {
        LinkedHashMap<String, Integer> weightedTags = new LinkedHashMap<>();
        int rank = 0;
        for (String tagText : tagTexts) {
            String normalizedTag = normalizeTagText(tagText);
            if (!StringUtils.hasText(normalizedTag) || weightedTags.containsKey(normalizedTag)) {
                continue;
            }
            weightedTags.put(normalizedTag, Math.max(1, 24 - rank));
            rank += 1;
        }
        return weightedTags;
    }

    private double recommendationScore(PictureAsset asset, Set<String> tagTexts, LinkedHashMap<String, Integer> interestTagWeights) {
        if (tagTexts == null || tagTexts.isEmpty() || interestTagWeights == null) {
            return 0;
        }
        double score = 0;
        for (Map.Entry<String, Integer> entry : interestTagWeights.entrySet()) {
            if (tagTexts.contains(entry.getKey())) {
                score += entry.getValue();
            }
        }
        return score;
    }

    private List<UUID> resolveTeamSpaceIds(boolean isAdmin, UUID requesterId) {
        if (isAdmin || requesterId == null) {
            return List.of();
        }
        List<UUID> teamIds = teamMemberRepository.findByUserIdAndStatus(
                requesterId, TeamMemberStatus.ACTIVE
        ).stream().map(TeamMember::getTeamId).toList();
        if (teamIds.isEmpty()) {
            return List.of();
        }
        return spaceRepository.findByTeamIdIn(teamIds).stream()
                .map(Space::getId)
                .toList();
    }

    private TeamMember resolveActiveTeamMember(Space space, UUID requesterId) {
        if (space.getType() != SpaceType.TEAM || space.getTeamId() == null || requesterId == null) {
            return null;
        }
        return teamMemberRepository.findByTeamIdAndUserId(space.getTeamId(), requesterId)
                .filter(member -> member.getStatus() == TeamMemberStatus.ACTIVE)
                .orElse(null);
    }

    private boolean canView(PictureAsset asset, Space space, UUID requesterId, boolean isAdmin, TeamMember activeTeamMember) {
        if (isAdmin) return true;
        if (requesterId != null && requesterId.equals(asset.getOwnerId())) return true;
        if (asset.getVisibility() == Visibility.PUBLIC && asset.getReviewStatus() == ReviewStatus.APPROVED) return true;
        if (space.getType() == SpaceType.TEAM && activeTeamMember != null && asset.getVisibility() != Visibility.PRIVATE) return true;
        return false;
    }

    private Subquery<UUID> buildSearchSubquery(
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Root<PictureAsset> root,
            String likeValue
    ) {
        Subquery<UUID> subquery = query.subquery(UUID.class);
        var docRoot = subquery.from(com.cn.cloudpictureplatform.domain.search.PictureSearchDocument.class);
        subquery.select(docRoot.get("pictureId"))
                .where(
                        builder.equal(docRoot.get("pictureId"), root.get("id")),
                        builder.like(builder.lower(docRoot.get("content")), likeValue)
                );
        return subquery;
    }

    private void applyKeywordOrdering(
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Root<PictureAsset> root,
            String normalizedKeyword, String likeValue
    ) {
        if (query == null || !StringUtils.hasText(normalizedKeyword)) return;
        Expression<Integer> exactNameScore = builder.<Integer>selectCase()
                .when(builder.equal(builder.lower(root.get("name")), normalizedKeyword), 120).otherwise(0);
        Expression<Integer> prefixNameScore = builder.<Integer>selectCase()
                .when(builder.like(builder.lower(root.get("name")), normalizedKeyword + "%"), 70).otherwise(0);
        Expression<Integer> containsNameScore = builder.<Integer>selectCase()
                .when(builder.like(builder.lower(root.get("name")), likeValue), 40).otherwise(0);
        Expression<Integer> filenameScore = builder.<Integer>selectCase()
                .when(builder.like(builder.lower(root.get("originalFilename")), likeValue), 20).otherwise(0);
        Expression<Integer> documentScore = builder.<Integer>selectCase()
                .when(builder.exists(buildSearchSubquery(query, builder, root, likeValue)), 12).otherwise(0);
        Expression<Integer> relevanceScore = builder.sum(
                builder.sum(exactNameScore, prefixNameScore),
                builder.sum(containsNameScore, builder.sum(filenameScore, documentScore))
        );
        query.orderBy(
                builder.desc(relevanceScore),
                builder.desc(root.get("updatedAt")),
                builder.desc(root.get("createdAt"))
        );
    }

    private Sort resolveKeywordAwareSort(String keyword, String sortBy, String sortDir) {
        if (!StringUtils.hasText(sortBy) && StringUtils.hasText(keyword)) return Sort.unsorted();
        if (!StringUtils.hasText(sortBy)) return Sort.by("createdAt").descending();
        return resolvePictureSort(sortBy, sortDir);
    }

    private Sort resolvePictureSort(String sortBy, String sortDir) {
        String resolvedSortBy = StringUtils.hasText(sortBy) ? sortBy : "createdAt";
        Set<String> allowedFields = Set.of("createdAt", "updatedAt", "sizeBytes");
        if (!allowedFields.contains(resolvedSortBy)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid sort field");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (StringUtils.hasText(sortDir)) {
            if ("asc".equalsIgnoreCase(sortDir)) direction = Sort.Direction.ASC;
            else if (!"desc".equalsIgnoreCase(sortDir)) throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid sort direction");
        }
        return Sort.by(direction, resolvedSortBy);
    }

    private String normalizeTagText(String tagText) {
        return tagText == null ? null : tagText.trim().toLowerCase();
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? null : keyword.trim().toLowerCase();
    }
}
