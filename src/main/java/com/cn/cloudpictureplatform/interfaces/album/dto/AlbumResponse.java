package com.cn.cloudpictureplatform.interfaces.album.dto;

import java.time.Instant;
import java.util.UUID;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumResponse {
    private UUID id;
    private UUID spaceId;
    private String name;
    private String description;
    private UUID coverPictureId;
    private Visibility visibility;
    private int sortOrder;
    private int pictureCount;
    private Instant createdAt;
    private Instant updatedAt;
}
