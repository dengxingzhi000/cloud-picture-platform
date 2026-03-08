package com.cn.cloudpictureplatform.interfaces.tag;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.tag.TagService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.interfaces.tag.dto.TagCreateRequest;
import com.cn.cloudpictureplatform.interfaces.tag.dto.TagResponse;
import com.cn.cloudpictureplatform.interfaces.tag.dto.TagUpdateRequest;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PostMapping
    public ApiResponse<TagResponse> create(@Valid @RequestBody TagCreateRequest request) {
        return ApiResponse.ok(tagService.createTag(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<TagResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(tagService.listTags(page, size, keyword));
    }

    @PatchMapping("/{id}")
    public ApiResponse<TagResponse> update(
            @PathVariable("id") UUID tagId,
            @Valid @RequestBody TagUpdateRequest request
    ) {
        return ApiResponse.ok(tagService.updateTag(tagId, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") UUID tagId) {
        tagService.deleteTag(tagId);
        return ApiResponse.ok(null);
    }
}
