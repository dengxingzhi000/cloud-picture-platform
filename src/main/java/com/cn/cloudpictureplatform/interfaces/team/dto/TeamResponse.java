package com.cn.cloudpictureplatform.interfaces.team.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID ownerId;
    private UUID spaceId;
    private Instant createdAt;
}
