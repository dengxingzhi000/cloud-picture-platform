package com.cn.cloudpictureplatform.interfaces.picture;

import com.cn.cloudpictureplatform.application.picture.BatchPictureService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.picture.dto.BatchOperationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pictures/batch")
@RequiredArgsConstructor
public class BatchPictureController {

    private final BatchPictureService batchPictureService;

    @PostMapping("/delete")
    public ApiResponse<Integer> batchDelete(
            @Valid @RequestBody BatchOperationRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(batchPictureService.batchDelete(request.getPictureIds(), principal.getId()));
    }

    @PostMapping("/visibility")
    public ApiResponse<Integer> batchUpdateVisibility(
            @Valid @RequestBody BatchOperationRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(batchPictureService.batchUpdateVisibility(
                request.getPictureIds(), request.getVisibility(), principal.getId()));
    }

    @PostMapping("/tag")
    public ApiResponse<Integer> batchTag(
            @Valid @RequestBody BatchOperationRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(batchPictureService.batchAddTag(
                request.getPictureIds(), request.getTagTexts(), principal.getId()));
    }

    @PostMapping("/move")
    public ApiResponse<Integer> batchMove(
            @Valid @RequestBody BatchOperationRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(batchPictureService.batchMoveToAlbum(
                request.getPictureIds(), request.getTargetAlbumId(), principal.getId()));
    }
}
