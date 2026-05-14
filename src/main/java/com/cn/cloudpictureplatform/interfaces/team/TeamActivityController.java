package com.cn.cloudpictureplatform.interfaces.team;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.team.TeamActivityService;
import com.cn.cloudpictureplatform.application.team.TeamActivityService.TeamActivityResponse;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/teams/{teamId}/activities")
public class TeamActivityController {
    private final TeamActivityService teamActivityService;

    public TeamActivityController(TeamActivityService teamActivityService) {
        this.teamActivityService = teamActivityService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TeamActivityResponse>> listActivities(
            @PathVariable("teamId") UUID teamId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(teamActivityService.listActivities(teamId, page, size));
    }
}
