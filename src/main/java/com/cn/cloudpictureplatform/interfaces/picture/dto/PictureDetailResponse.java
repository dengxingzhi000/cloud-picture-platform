package com.cn.cloudpictureplatform.interfaces.picture.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PictureDetailResponse {

    private UUID id;
    private String name;
    private String originalFilename;
    private String url;
    private String contentType;
    private long sizeBytes;
    private String checksum;
    private Integer width;
    private Integer height;
    private Visibility visibility;
    private ReviewStatus reviewStatus;
    private UUID ownerId;
    private String ownerUsername;
    private String ownerDisplayName;
    private UUID spaceId;
    private String spaceName;
    private SpaceType spaceType;
    private UUID teamId;
    private String teamName;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean canEdit;
    private boolean canManage;
    private boolean canJoinCollaboration;
    private List<PictureTagResponse> tags;
}
