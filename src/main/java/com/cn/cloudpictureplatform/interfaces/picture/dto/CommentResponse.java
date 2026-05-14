package com.cn.cloudpictureplatform.interfaces.picture.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CommentResponse {
    private UUID id;
    private UUID pictureId;
    private UUID authorId;
    private String authorUsername;
    private String authorDisplayName;
    private String content;
    private UUID parentId;
    private Double x;
    private Double y;
    private boolean resolved;
    private int replyCount;
    private Instant createdAt;
    private Instant updatedAt;
}
