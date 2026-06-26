package com.cn.cloudpictureplatform.interfaces.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.domain.ai.AiChatHistory;
import com.cn.cloudpictureplatform.domain.ai.AiChatRequest;
import com.cn.cloudpictureplatform.domain.ai.AiChatResponse;
import com.cn.cloudpictureplatform.domain.ai.AiGateway;
import com.cn.cloudpictureplatform.common.security.AppUserPrincipal;
import com.cn.cloudpictureplatform.application.ai.AiChatHistoryService;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@ConditionalOnBean(AiGateway.class)
public class AiAssistantController {
    private final AiGateway aiGateway;
    private final AiChatHistoryService chatHistoryService;

    public AiAssistantController(AiGateway aiGateway, AiChatHistoryService chatHistoryService) {
        this.aiGateway = aiGateway;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(
            @RequestBody AiChatRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        chatHistoryService.saveMessage(
                request.sessionId(),
                principal.getId(),
                "user",
                request.message(),
                null,
                request.contextPictureIds()
        );

        AiChatResponse response = aiGateway.chat(request);

        chatHistoryService.saveMessage(
                request.sessionId(),
                principal.getId(),
                "assistant",
                response.reply(),
                response.intent(),
                null
        );

        return ApiResponse.ok(response);
    }
}
