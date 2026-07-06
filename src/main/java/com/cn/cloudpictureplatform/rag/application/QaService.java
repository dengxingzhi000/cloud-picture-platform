package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.domain.RetrievalResult;
import com.cn.cloudpictureplatform.rag.infrastructure.generator.DeepSeekGenerationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

    private final RetrievalService retrievalService;
    private final RerankService rerankService;
    private final DeepSeekGenerationClient generationClient;

    private static final String SYSTEM_PROMPT = """
        你是一个企业知识库问答助手。请根据以下检索到的文档片段回答用户问题。

        规则：
        1. 只根据提供的文档内容回答，不要编造信息
        2. 如果文档中没有相关信息，请明确说明
        3. 回答末尾标注引用来源，格式：[文档名 > 章节路径]
        4. 回答要简洁、准确、专业
        """;

    public QaResponse ask(String query) {
        List<RetrievalResult> retrieved = retrievalService.retrieve(query);
        List<RetrievalResult> reranked = rerankService.rerank(query, retrieved);

        String context = buildContext(reranked);

        String userPrompt = "检索到的文档：\n" + context + "\n\n用户问题：" + query;
        String answer = generationClient.generate(SYSTEM_PROMPT, userPrompt);

        List<String> citations = reranked.stream()
            .map(r -> r.title() + (r.sectionPath() != null ? " > " + r.sectionPath() : ""))
            .distinct()
            .collect(Collectors.toList());

        return new QaResponse(answer, citations, reranked.size());
    }

    private String buildContext(List<RetrievalResult> results) {
        return results.stream()
            .map(r -> String.format("[文档: %s, 章节: %s, 页码: %d]\n%s",
                r.title(),
                r.sectionPath() != null ? r.sectionPath() : "无",
                r.pageNumber(),
                r.content()))
            .collect(Collectors.joining("\n\n---\n\n"));
    }

    public record QaResponse(String answer, List<String> citations, int chunksUsed) {}
}
