package com.cn.cloudpictureplatform.application.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class PermissionCreateRequest {
    @NotBlank @Size(max = 100)
    private String name;
    @Size(max = 200)
    private String description;
    @NotBlank @Size(max = 50)
    private String resource;
    @NotBlank @Size(max = 50)
    private String action;
}
