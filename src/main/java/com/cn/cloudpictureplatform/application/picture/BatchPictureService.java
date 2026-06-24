package com.cn.cloudpictureplatform.application.picture;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.PictureTag;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BatchPictureService {

    private final PictureAssetRepository pictureAssetRepository;
    private final PictureTagRepository pictureTagRepository;
    private final com.cn.cloudpictureplatform.application.album.AlbumService albumService;

    @Transactional
    public int batchDelete(List<UUID> pictureIds, UUID requesterId) {
        List<PictureAsset> assets = findAuthorizedAssets(pictureIds, requesterId);
        for (PictureAsset asset : assets) {
            pictureTagRepository.deleteByPictureAssetId(asset.getId());
        }
        pictureAssetRepository.deleteAll(assets);
        return assets.size();
    }

    @Transactional
    public int batchUpdateVisibility(List<UUID> pictureIds, Visibility visibility, UUID requesterId) {
        if (visibility == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "visibility is required");
        }
        List<PictureAsset> assets = findAuthorizedAssets(pictureIds, requesterId);
        for (PictureAsset asset : assets) {
            asset.changeVisibility(visibility);
        }
        pictureAssetRepository.saveAll(assets);
        return assets.size();
    }

    @Transactional
    public int batchAddTag(List<UUID> pictureIds, List<String> tagTexts, UUID requesterId) {
        if (tagTexts == null || tagTexts.isEmpty()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "tagTexts is required");
        }
        List<PictureAsset> assets = findAuthorizedAssets(pictureIds, requesterId);
        for (PictureAsset asset : assets) {
            List<PictureTag> existingTags = pictureTagRepository.findByPictureAssetId(asset.getId());
            Set<String> existing = existingTags.stream()
                    .map(PictureTag::getTagText)
                    .collect(Collectors.toSet());
            for (String tagText : tagTexts) {
                if (!existing.contains(tagText)) {
                    pictureTagRepository.save(PictureTag.builder()
                            .pictureAssetId(asset.getId())
                            .tagText(tagText)
                            .build());
                }
            }
        }
        return assets.size();
    }

    @Transactional
    public int batchMoveToAlbum(List<UUID> pictureIds, UUID targetAlbumId, UUID requesterId) {
        if (targetAlbumId == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "targetAlbumId is required");
        }
        List<PictureAsset> assets = findAuthorizedAssets(pictureIds, requesterId);
        albumService.addPictures(targetAlbumId, assets.stream().map(PictureAsset::getId).toList());
        return assets.size();
    }

    private List<PictureAsset> findAuthorizedAssets(List<UUID> pictureIds, UUID userId) {
        List<PictureAsset> assets = pictureAssetRepository.findAllById(pictureIds);
        for (PictureAsset asset : assets) {
            if (!asset.isOwnedBy(userId)) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "not authorized for picture: " + asset.getId());
            }
        }
        return assets;
    }
}
