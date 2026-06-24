package com.cn.cloudpictureplatform.interfaces.admin;

import com.cn.cloudpictureplatform.application.admin.AiStatsService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.interfaces.admin.dto.AiStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AiStatsService aiStatsService;

    @GetMapping("/stats")
    public ApiResponse<AiStatsResponse> getStats() {
        return ApiResponse.ok(aiStatsService.getStats());
    }
}
