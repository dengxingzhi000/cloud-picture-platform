package com.cn.cloudpictureplatform.interfaces.admin.dto;

import java.time.Instant;
import java.util.UUID;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPictureSummary {

    private UUID id;
    private String name;
    private String url;
    private Visibility visibility;
    private ReviewStatus reviewStatus;
    private long sizeBytes;
    private Integer width;
    private Integer height;
    private UUID ownerId;
    private String ownerUsername;
    private String ownerDisplayName;
    private UUID lastReviewerId;
    private String lastReviewerUsername;
    private String lastReviewerDisplayName;
    private Instant lastReviewedAt;
}
