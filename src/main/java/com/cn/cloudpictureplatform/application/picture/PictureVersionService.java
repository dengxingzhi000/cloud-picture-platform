package com.cn.cloudpictureplatform.application.picture;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.PictureVersion;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureVersionRepository;
import com.cn.cloudpictureplatform.application.shared.dto.PictureVersionResponse;

@Service
@Transactional(readOnly = true)
public class PictureVersionService {
    private final PictureVersionRepository pictureVersionRepository;
    private final PictureAssetRepository pictureAssetRepository;

    public PictureVersionService(
            PictureVersionRepository pictureVersionRepository,
            PictureAssetRepository pictureAssetRepository
    ) {
        this.pictureVersionRepository = pictureVersionRepository;
        this.pictureAssetRepository = pictureAssetRepository;
    }

    public List<PictureVersionResponse> listVersions(UUID pictureId) {
        return pictureVersionRepository.findByPictureIdOrderByVersionDesc(pictureId).stream()
                .map(this::toResponse)
                .toList();
    }

    public PictureVersionResponse getVersion(UUID pictureId, int version) {
        PictureVersion pv = pictureVersionRepository.findByPictureIdAndVersion(pictureId, version)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "version not found"));
        return toResponse(pv);
    }

    @Transactional
    public void snapshotCurrentVersion(UUID pictureId, UUID userId, String changeNote) {
        PictureAsset asset = pictureAssetRepository.findById(pictureId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "picture not found"));
        int nextVersion = pictureVersionRepository.findMaxVersionByPictureId(pictureId) + 1;
        PictureVersion pv = PictureVersion.builder()
                .pictureId(pictureId)
                .version(nextVersion)
                .storageKey(asset.getStorageKey())
                .fileContentId(asset.getFileContentId())
                .fileSize(asset.getSizeBytes())
                .width(asset.getWidth())
                .height(asset.getHeight())
                .checksum(asset.getChecksum())
                .changeNote(changeNote)
                .createdByUserId(userId)
                .build();
        pictureVersionRepository.save(pv);
    }

    private PictureVersionResponse toResponse(PictureVersion pv) {
        return PictureVersionResponse.builder()
                .id(pv.getId()).pictureId(pv.getPictureId()).version(pv.getVersion())
                .fileSize(pv.getFileSize()).width(pv.getWidth()).height(pv.getHeight())
                .changeNote(pv.getChangeNote()).createdByUserId(pv.getCreatedByUserId())
                .createdAt(pv.getCreatedAt()).build();
    }
}
