package com.cn.cloudpictureplatform.interfaces.excalidraw;

import com.cn.cloudpictureplatform.application.excalidraw.ExcalidrawSceneService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.excalidraw.dto.CreateExcalidrawSceneRequest;
import com.cn.cloudpictureplatform.interfaces.excalidraw.dto.ExcalidrawSceneResponse;
import com.cn.cloudpictureplatform.interfaces.excalidraw.dto.UpdateSnapshotRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scenes")
@RequiredArgsConstructor
public class ExcalidrawSceneController {

    private final ExcalidrawSceneService sceneService;

    @PostMapping
    public ApiResponse<ExcalidrawSceneResponse> createScene(
            @RequestBody CreateExcalidrawSceneRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        UUID userId = principal.getId();
        return ApiResponse.ok(sceneService.createScene(request, userId));
    }

    @GetMapping("/{sceneId}")
    public ApiResponse<ExcalidrawSceneResponse> getScene(@PathVariable UUID sceneId) {
        return ApiResponse.ok(sceneService.getScene(sceneId));
    }

    @GetMapping("/{sceneId}/snapshot")
    public ApiResponse<ExcalidrawSceneResponse> getSnapshot(@PathVariable UUID sceneId) {
        return ApiResponse.ok(sceneService.getScene(sceneId));
    }

    @GetMapping("/by-picture/{pictureId}")
    public ApiResponse<ExcalidrawSceneResponse> getSceneByPictureId(@PathVariable UUID pictureId) {
        return ApiResponse.ok(sceneService.getSceneByPictureId(pictureId));
    }

    @DeleteMapping("/{sceneId}")
    public ApiResponse<Void> deleteScene(@PathVariable UUID sceneId) {
        sceneService.deleteScene(sceneId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{sceneId}/snapshot")
    public ApiResponse<ExcalidrawSceneResponse> updateSnapshot(
            @PathVariable UUID sceneId,
            @RequestBody UpdateSnapshotRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        UUID userId = principal.getId();
        return ApiResponse.ok(sceneService.updateSnapshot(sceneId, request.getSnapshotData(), null, userId));
    }
}
