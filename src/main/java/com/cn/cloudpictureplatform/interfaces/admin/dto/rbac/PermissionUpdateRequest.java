package com.cn.cloudpictureplatform.interfaces.admin.dto.rbac;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PermissionUpdateRequest {

    @Size(max = 100)
    private String name;

    @Size(max = 200)
    private String description;

    @Size(max = 50)
    private String resource;

    @Size(max = 50)
    private String action;
}
