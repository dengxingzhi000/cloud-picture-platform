package com.cn.cloudpictureplatform.application.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.cn.cloudpictureplatform.domain.picture.Visibility;

@Getter
@Setter
@NoArgsConstructor
public class AlbumCreateRequest {
    @NotBlank @Size(max = 100)
    private String name;
    @Size(max = 500)
    private String description;
    private Visibility visibility;
    private Integer sortOrder;
}
