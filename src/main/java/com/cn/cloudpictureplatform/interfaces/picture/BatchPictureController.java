package com.cn.cloudpictureplatform.interfaces.picture;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.PictureTag;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureTagRepository;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.interfaces.picture.dto.BatchOperationRequest;

@RestController
@RequestMapping("/api/pictures/batch")
public class BatchPictureController {
    private final PictureAssetRepository pictureAssetRepository;
    private final PictureTagRepository pictureTagRepository;

    public BatchPictureController(
            PictureAssetRepository pictureAssetRepository,
            PictureTagRepository pictureTagRepository
    ) {
        this.pictureAssetRepository = pictureAssetRepository;
        this.pictureTagRepository = pictureTagRepository;
    }

    @PostMapping("/delete")
    @Transactional
    public ApiResponse<Integer> batchDelete(
            @Valid @RequestBody BatchOperationRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        List<PictureAsset> assets = findAuthorizedAssets(request.getPictureIds(), principal.getId());
        pictureAssetRepository.deleteAll(assets);
        return ApiResponse.ok(assets.size());
    }

    @PostMapping("/visibility")
    @Transactional
    public ApiResponse<Integer> batchUpdateVisibility(
            @Valid @RequestBody BatchOperationRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        if (request.getVisibility() == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "visibility is required");
        }
        List<PictureAsset> assets = findAuthorizedAssets(request.getPictureIds(), principal.getId());
        for (PictureAsset asset : assets) {
            asset.setVisibility(request.getVisibility());
        }
        pictureAssetRepository.saveAll(assets);
        return ApiResponse.ok(assets.size());
    }

    @PostMapping("/tag")
    @Transactional
    public ApiResponse<Integer> batchTag(
            @Valid @RequestBody BatchOperationRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        if (request.getTagTexts() == null || request.getTagTexts().isEmpty()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "tagTexts is required");
        }
        List<PictureAsset> assets = findAuthorizedAssets(request.getPictureIds(), principal.getId());
        for (PictureAsset asset : assets) {
            List<String> existingTags = pictureTagRepository.findByPictureAssetId(asset.getId())
                    .stream().map(PictureTag::getTagText).toList();
            for (String tagText : request.getTagTexts()) {
                if (!existingTags.contains(tagText)) {
                    pictureTagRepository.save(PictureTag.builder()
                            .pictureAssetId(asset.getId())
                            .tagText(tagText)
                            .build());
                }
            }
        }
        return ApiResponse.ok(assets.size());
    }

    private List<PictureAsset> findAuthorizedAssets(List<UUID> pictureIds, UUID userId) {
        List<PictureAsset> assets = pictureAssetRepository.findAllById(pictureIds);
        for (PictureAsset asset : assets) {
            if (!userId.equals(asset.getOwnerId())) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "not authorized for picture: " + asset.getId());
            }
        }
        return assets;
    }
}
