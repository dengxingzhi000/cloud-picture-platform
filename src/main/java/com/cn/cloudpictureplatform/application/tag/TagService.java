package com.cn.cloudpictureplatform.application.tag;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.util.StringUtils;
import com.cn.cloudpictureplatform.application.search.SearchIndexService;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.picture.PictureTag;
import com.cn.cloudpictureplatform.domain.picture.Tag;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureTagRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TagRepository;
import com.cn.cloudpictureplatform.interfaces.tag.dto.TagCreateRequest;
import com.cn.cloudpictureplatform.interfaces.tag.dto.TagResponse;
import com.cn.cloudpictureplatform.interfaces.tag.dto.TagUpdateRequest;

@Service
public class TagService {
    private final TagRepository tagRepository;
    private final PictureTagRepository pictureTagRepository;
    private final SearchIndexService searchIndexService;

    public TagService(
            TagRepository tagRepository,
            PictureTagRepository pictureTagRepository,
            SearchIndexService searchIndexService
    ) {
        this.tagRepository = tagRepository;
        this.pictureTagRepository = pictureTagRepository;
        this.searchIndexService = searchIndexService;
    }

    @Transactional
    @CacheEvict(cacheNames = {"tagCatalog", "pictureSearch"}, allEntries = true)
    public TagResponse createTag(TagCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "tag name is required");
        }
        String name = request.getName().trim();
        if (tagRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "tag already exists");
        }
        Tag tag = new Tag();
        tag.setName(name);
        Tag saved = tagRepository.save(tag);
        return toResponse(saved);
    }

    @Cacheable(cacheNames = "tagCatalog", key = "T(java.util.Arrays).asList(#page,#size,#keyword)")
    public PageResponse<TagResponse> listTags(int page, int size, String keyword) {
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), 100);
        var pageable = PageRequest.of(pageIndex, pageSize, Sort.by("name").ascending());
        var result = StringUtils.hasText(keyword)
                ? tagRepository.findByNameContainingIgnoreCase(keyword.trim(), pageable)
                : tagRepository.findAll(pageable);
        var items = result.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, result.getTotalElements(), pageIndex, pageSize);
    }

    @Transactional
    @CacheEvict(cacheNames = {"tagCatalog", "pictureSearch"}, allEntries = true)
    public TagResponse updateTag(UUID tagId, TagUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "tag name is required");
        }
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "tag not found"));
        String name = request.getName().trim();
        tagRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(tag.getId()))
                .ifPresent(existing -> {
                    throw new ApiException(ApiErrorCode.BAD_REQUEST, "tag already exists");
                });
        tag.setName(name);
        Tag saved = tagRepository.save(tag);
        List<PictureTag> tagItems = pictureTagRepository.findByTagId(tagId);
        tagItems.forEach(tagItem -> tagItem.setTagText(name));
        pictureTagRepository.saveAll(tagItems);
        tagItems.stream()
                .map(PictureTag::getPictureAssetId)
                .distinct()
                .forEach(searchIndexService::enqueuePicture);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {"tagCatalog", "pictureSearch"}, allEntries = true)
    public void deleteTag(UUID tagId) {
        if (pictureTagRepository.countByTagId(tagId) > 0) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "tag is in use");
        }
        if (!tagRepository.existsById(tagId)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "tag not found");
        }
        tagRepository.deleteById(tagId);
    }

    private TagResponse toResponse(Tag tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .createdAt(tag.getCreatedAt())
                .build();
    }
}
