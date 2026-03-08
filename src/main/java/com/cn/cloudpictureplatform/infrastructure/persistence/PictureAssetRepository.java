package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;

public interface PictureAssetRepository extends JpaRepository<PictureAsset, UUID>, JpaSpecificationExecutor<PictureAsset> {
    Page<PictureAsset> findByVisibilityAndReviewStatus(
            Visibility visibility,
            ReviewStatus reviewStatus,
            Pageable pageable
    );

    Page<PictureAsset> findByReviewStatus(ReviewStatus reviewStatus, Pageable pageable);
}
