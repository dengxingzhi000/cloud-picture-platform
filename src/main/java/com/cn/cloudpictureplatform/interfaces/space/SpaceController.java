package com.cn.cloudpictureplatform.interfaces.space;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.space.SpaceQuotaService;
import com.cn.cloudpictureplatform.application.space.SpaceQuotaService.SpaceUsageResponse;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/v1/spaces")
public class SpaceController {
    private final SpaceQuotaService spaceQuotaService;

    public SpaceController(SpaceQuotaService spaceQuotaService) {
        this.spaceQuotaService = spaceQuotaService;
    }

    @GetMapping("/{id}/usage")
    public ApiResponse<SpaceUsageResponse> getUsage(
            @PathVariable("id") UUID spaceId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(spaceQuotaService.getUsage(spaceId));
    }
}
