package com.cn.cloudpictureplatform.interfaces.picture;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import com.cn.cloudpictureplatform.application.picture.PictureService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureTagCreateRequest;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureTagResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureSummary;

@Validated
@RestController
@RequestMapping("/api/pictures")
public class PictureController {
    private final PictureService pictureService;

    public PictureController(PictureService pictureService) {
        this.pictureService = pictureService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PictureResponse> upload(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") Visibility visibility,
            @RequestParam(value = "name", required = false) String name
    ) {
        return ApiResponse.ok(pictureService.upload(principal.getId(), file, visibility, name));
    }

    @GetMapping("/public")
    public ApiResponse<PageResponse<PictureSummary>> listPublic(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long minSizeBytes,
            @RequestParam(required = false) Long maxSizeBytes,
            @RequestParam(required = false) String orientation
    ) {
        return ApiResponse.ok(pictureService.listPublic(
                page,
                size,
                keyword,
                minSizeBytes,
                maxSizeBytes,
                orientation
        ));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<PictureSummary>> search(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) UUID spaceId,
            @RequestParam(required = false) Visibility visibility,
            @RequestParam(required = false) ReviewStatus reviewStatus,
            @RequestParam(required = false) Long minSizeBytes,
            @RequestParam(required = false) Long maxSizeBytes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore,
            @RequestParam(required = false) String orientation,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        return ApiResponse.ok(pictureService.searchPictures(
                page,
                size,
                keyword,
                ownerId,
                spaceId,
                visibility,
                reviewStatus,
                minSizeBytes,
                maxSizeBytes,
                createdAfter,
                createdBefore,
                orientation,
                tag,
                tagId,
                sortBy,
                sortDir,
                principal == null ? null : principal.getId(),
                principal == null ? null : principal.getRole()
        ));
    }

    @GetMapping("/{id}/tags")
    public ApiResponse<List<PictureTagResponse>> listTags(@PathVariable("id") UUID pictureId) {
        return ApiResponse.ok(pictureService.listTags(pictureId));
    }

    @PostMapping("/{id}/tags")
    public ApiResponse<List<PictureTagResponse>> addTags(
            @PathVariable("id") UUID pictureId,
            @Valid @RequestBody PictureTagCreateRequest request
    ) {
        return ApiResponse.ok(pictureService.addTags(pictureId, request));
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    public ApiResponse<Void> removeTag(
            @PathVariable("id") UUID pictureId,
            @PathVariable("tagId") UUID tagId
    ) {
        pictureService.removeTag(pictureId, tagId);
        return ApiResponse.ok(null);
    }
}
