package com.cn.cloudpictureplatform.interfaces.picture;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.picture.PictureVersionService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureVersionResponse;

@RestController
@RequestMapping("/api/v1/pictures/{pictureId}/versions")
public class PictureVersionController {
    private final PictureVersionService pictureVersionService;

    public PictureVersionController(PictureVersionService pictureVersionService) {
        this.pictureVersionService = pictureVersionService;
    }

    @GetMapping
    public ApiResponse<List<PictureVersionResponse>> listVersions(
            @PathVariable("pictureId") UUID pictureId
    ) {
        return ApiResponse.ok(pictureVersionService.listVersions(pictureId));
    }

    @GetMapping("/{version}")
    public ApiResponse<PictureVersionResponse> getVersion(
            @PathVariable("pictureId") UUID pictureId,
            @PathVariable("version") int version
    ) {
        return ApiResponse.ok(pictureVersionService.getVersion(pictureId, version));
    }

    @PostMapping
    public ApiResponse<Void> createVersion(
            @PathVariable("pictureId") UUID pictureId,
            @RequestParam(required = false) String changeNote,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        pictureVersionService.snapshotCurrentVersion(pictureId, principal.getId(), changeNote);
        return ApiResponse.ok(null);
    }
}
