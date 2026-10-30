package com.cn.cloudpictureplatform.rag.infrastructure.generator;

import reactor.core.publisher.Flux;

public interface GenerationClient {
    String generate(String systemPrompt, String userPrompt);

    Flux<String> generateStream(String systemPrompt, String userPrompt);
}
