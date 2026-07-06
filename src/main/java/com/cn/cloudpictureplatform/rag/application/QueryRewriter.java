package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.domain.ConversationMessage;
import com.cn.cloudpictureplatform.rag.infrastructure.generator.DeepSeekGenerationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRewriter {

    private final DeepSeekGenerationClient generationClient;

    private static final String REWRITE_PROMPT = """
        你是一个查询改写助手。根据对话历史，将用户的追问改写成独立、完整的检索查询。

        规则：
        1. 如果用户的问题已经是独立完整的，直接返回原问题
        2. 如果用户使用了代词（"这个"、"那个"、"它"等），根据上下文替换为具体指代
        3. 只返回改写后的查询，不要添加任何解释
        """;

    public String rewrite(String currentQuery, List<ConversationMessage> history) {
        if (history.isEmpty()) {
            return currentQuery;
        }

        String historyContext = history.stream()
            .map(m -> m.getRole() + ": " + m.getContent())
            .collect(Collectors.joining("\n"));

        String prompt = "对话历史：\n" + historyContext + "\n\n当前问题：" + currentQuery;

        try {
            String rewritten = generationClient.generate(REWRITE_PROMPT, prompt);

            if (rewritten == null || rewritten.isBlank() || rewritten.length() < 3) {
                log.warn("Query rewriting produced empty result, using original");
                return currentQuery;
            }

            log.debug("Query rewritten: '{}' → '{}'", currentQuery, rewritten);
            return rewritten.trim();
        } catch (Exception e) {
            log.warn("Query rewriting failed, using original: {}", e.getMessage());
            return currentQuery;
        }
    }
}