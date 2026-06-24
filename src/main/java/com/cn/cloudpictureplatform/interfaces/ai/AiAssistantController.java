package com.cn.cloudpictureplatform.interfaces.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.infrastructure.ai.dto.AiChatRequest;
import com.cn.cloudpictureplatform.infrastructure.ai.dto.AiChatResponse;
import com.cn.cloudpictureplatform.infrastructure.ai.gateway.AiGateway;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/v1/ai")
@ConditionalOnBean(AiGateway.class)
public class AiAssistantController {
    private final AiGateway aiGateway;

    public AiAssistantController(AiGateway aiGateway) {
        this.aiGateway = aiGateway;
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(
            @RequestBody AiChatRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ApiResponse.ok(aiGateway.chat(request));
    }
}
