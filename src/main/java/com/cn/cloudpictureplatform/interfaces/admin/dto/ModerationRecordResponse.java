package com.cn.cloudpictureplatform.interfaces.admin.dto;

import java.time.Instant;
import java.util.UUID;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationRecordResponse {

    private UUID id;
    private UUID pictureId;
    private UUID reviewerId;
    private String reviewerUsername;
    private String reviewerDisplayName;
    private ReviewStatus fromStatus;
    private ReviewStatus toStatus;
    private String reason;
    private Instant reviewedAt;
}
