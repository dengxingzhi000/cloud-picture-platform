package com.cn.cloudpictureplatform.interfaces.tag.dto;

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
public class TagResponse {

    private UUID id;
    private String name;
    private Instant createdAt;
}
