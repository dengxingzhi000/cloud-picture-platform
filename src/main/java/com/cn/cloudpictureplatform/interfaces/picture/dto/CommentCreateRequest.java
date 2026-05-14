package com.cn.cloudpictureplatform.interfaces.picture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CommentCreateRequest {
    @NotBlank @Size(max = 2000)
    private String content;
    private UUID parentId;
    private Double x;
    private Double y;
}
