package com.cn.cloudpictureplatform.interfaces.picture.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.cn.cloudpictureplatform.domain.picture.Visibility;

@Getter
@Setter
@NoArgsConstructor
public class BatchOperationRequest {
    @NotEmpty
    private List<UUID> pictureIds;

    private UUID targetSpaceId;
    private Visibility visibility;
    private List<String> tagTexts;
}
