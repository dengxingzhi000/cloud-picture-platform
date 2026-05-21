package com.cn.cloudpictureplatform.application.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemResponse {
    private UUID id;
    private String name;
    private String code;
    private String path;
    private String icon;
    private Integer sortOrder;
    @Builder.Default
    private List<MenuItemResponse> children = new ArrayList<>();
}
