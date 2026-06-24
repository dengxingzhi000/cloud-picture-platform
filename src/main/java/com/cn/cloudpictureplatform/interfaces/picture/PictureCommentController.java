package com.cn.cloudpictureplatform.interfaces.picture;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.picture.PictureCommentService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.application.shared.dto.CommentCreateRequest;
import com.cn.cloudpictureplatform.application.shared.dto.CommentResponse;

@RestController
@RequestMapping("/api/v1/pictures/{pictureId}/comments")
public class PictureCommentController {
    private final PictureCommentService pictureCommentService;

    public PictureCommentController(PictureCommentService pictureCommentService) {
        this.pictureCommentService = pictureCommentService;
    }

    @PostMapping
    public ApiResponse<CommentResponse> createComment(
            @PathVariable("pictureId") UUID pictureId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(pictureCommentService.createComment(pictureId, principal.getId(), request));
    }

    @GetMapping
    public ApiResponse<List<CommentResponse>> listComments(
            @PathVariable("pictureId") UUID pictureId
    ) {
        return ApiResponse.ok(pictureCommentService.listComments(pictureId));
    }

    @PatchMapping("/{commentId}")
    public ApiResponse<CommentResponse> updateComment(
            @PathVariable("pictureId") UUID pictureId,
            @PathVariable("commentId") UUID commentId,
            @RequestBody String content,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(pictureCommentService.updateComment(commentId, principal.getId(), content));
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(
            @PathVariable("pictureId") UUID pictureId,
            @PathVariable("commentId") UUID commentId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        pictureCommentService.deleteComment(commentId, principal.getId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{commentId}/resolve")
    public ApiResponse<Void> resolveComment(
            @PathVariable("pictureId") UUID pictureId,
            @PathVariable("commentId") UUID commentId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        pictureCommentService.resolveComment(commentId, principal.getId());
        return ApiResponse.ok(null);
    }
}
