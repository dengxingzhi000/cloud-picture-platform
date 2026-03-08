package com.cn.cloudpictureplatform.interfaces.picture.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PictureTagItemRequest {

    @NotBlank
    @Size(max = 100)
    private String text;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double confidenceScore;
}
