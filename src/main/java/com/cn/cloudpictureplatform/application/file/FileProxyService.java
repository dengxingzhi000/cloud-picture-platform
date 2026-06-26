package com.cn.cloudpictureplatform.application.file;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.storage.StorageService;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileProxyService {

    private final PictureAssetRepository pictureAssetRepository;
    private final StorageService storageService;

    public String resolveAccessibleStorageKey(String path, AppUserPrincipal requester) {
        if (path.startsWith("public/")) {
            return resolvePublicFile(path);
        }
        if (requester == null) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED, "authentication required");
        }
        return resolveProtectedFile(path, requester);
    }

    private String resolvePublicFile(String path) {
        PictureAsset pic = getValidAssetOrThrow(path);
        if (pic.getVisibility() != Visibility.PUBLIC || !pic.isApproved()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "access denied");
        }
        return path;
    }

    private String resolveProtectedFile(String path, AppUserPrincipal requester) {
        PictureAsset pic = getValidAssetOrThrow(path);
        if (pic.isOwnedBy(requester.getId())) {
            return path;
        }
        if (pic.getVisibility() == Visibility.PUBLIC && pic.isApproved()) {
            return path;
        }
        throw new ApiException(ApiErrorCode.FORBIDDEN, "access denied");
    }

    private PictureAsset getValidAssetOrThrow(String storageKey) {
        PictureAsset pic = pictureAssetRepository.findByStorageKey(storageKey)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "file not found"));
        if (pic.isDeleted()) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "file not found");
        }
        return pic;
    }

    public InputStream getFileStream(String storageKey) {
        return storageService.retrieve(storageKey);
    }
}
