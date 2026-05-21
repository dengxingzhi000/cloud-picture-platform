package com.cn.cloudpictureplatform.application.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
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
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<MenuItemResponse> children = new ArrayList<>();
}
