package com.cn.cloudpictureplatform.rag.interfaces;

import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.rag.application.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag/admin")
@RequiredArgsConstructor
public class AdminRagController {

    private final EvaluationService evaluationService;

    @PostMapping("/evaluate")
    public ApiResponse<EvaluationService.EvaluationResult> evaluate() {
        return ApiResponse.ok(evaluationService.runEvaluation());
    }
}
