package com.cn.cloudpictureplatform.rag.infrastructure.rerank;

import java.util.List;

public interface RerankClient {
    List<RerankResult> rerank(String query, List<String> documents, int topN);
}
