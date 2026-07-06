package com.cn.cloudpictureplatform.rag.infrastructure.generator;

public interface GenerationClient {
    String generate(String systemPrompt, String userPrompt);
}
