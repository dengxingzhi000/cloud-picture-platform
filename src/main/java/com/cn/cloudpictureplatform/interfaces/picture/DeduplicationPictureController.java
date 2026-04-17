package com.cn.cloudpictureplatform.interfaces.picture;

import com.cn.cloudpictureplatform.application.picture.DeduplicationPictureUploadService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureResponse;
import com.cn.cloudpictureplatform.interfaces.picture.dto.RapidUploadCheckResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

/**
 * 支持去重的图片上传控制器
 * 提供秒传功能：检测重复文件，共用存储资源
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/pictures")
@RequiredArgsConstructor
public class DeduplicationPictureController {
    private final DeduplicationPictureUploadService deduplicationUploadService;

    /**
     * 智能上传（支持秒传）
     * 如果文件已存在，则复用已有存储，不重复上传
     *
     * @param principal  当前用户
     * @param file     文件
     * @param visibility 可见性
     * @param name     文件名
     * @param spaceId  空间ID
     * @return 上传结果
     */
    @PostMapping(value = "/upload/deduplication", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PictureResponse> uploadWithDeduplication(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") Visibility visibility,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "spaceId", required = false) UUID spaceId
    ) {
        PictureResponse response = deduplicationUploadService.uploadWithDeduplication(
                principal.getId(),
                file,
                visibility,
                name,
                spaceId
        );
        return ApiResponse.ok(response);
    }

    /**
     * 秒传预检测接口
     * 在上传前检查文件是否已存在，如果存在则直接返回已有文件信息（秒传）
     *
     * @param sha256Hash 文件的 SHA-256 哈希
     * @return 检测结果
     */
    @GetMapping("/check-duplicate")
    public ApiResponse<RapidUploadCheckResponse> checkDuplicate(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @NotBlank String sha256Hash
    ) {
        Optional<PictureAsset> existing = deduplicationUploadService.checkDuplicate(principal.getId(), sha256Hash);

        if (existing.isPresent()) {
            PictureAsset file = existing.get();
            return ApiResponse.ok(RapidUploadCheckResponse.exists(
                    file.getId(),
                    null,
                    file.getSizeBytes(),
                    file.getWidth(),
                    file.getHeight()
            ));
        } else {
            return ApiResponse.ok(RapidUploadCheckResponse.notExists());
        }
    }
}
