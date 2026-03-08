package com.cn.cloudpictureplatform.interfaces.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TagUpdateRequest {

    @NotBlank
    @Size(max = 80)
    private String name;
}
