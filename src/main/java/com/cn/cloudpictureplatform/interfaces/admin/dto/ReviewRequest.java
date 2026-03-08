package com.cn.cloudpictureplatform.interfaces.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReviewRequest {

    @NotNull
    private ReviewStatus status;

    @Size(max = 500)
    private String reason;
}
