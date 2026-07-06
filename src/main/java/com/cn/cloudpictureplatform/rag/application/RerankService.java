package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.RetrievalResult;
import com.cn.cloudpictureplatform.rag.infrastructure.rerank.QwenRerankClient;
import com.cn.cloudpictureplatform.rag.infrastructure.rerank.RerankResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    private final QwenRerankClient qwenRerankClient;
    private final RagProperties ragProperties;

    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates) {
        var config = ragProperties.rerank();

        if (!config.enabled() || candidates.isEmpty()) {
            return candidates;
        }

        List<String> contents = candidates.stream()
            .map(RetrievalResult::content)
            .toList();

        List<RerankResult> reranked = qwenRerankClient.rerank(query, contents, config.topN());

        return reranked.stream()
            .map(rr -> {
                RetrievalResult original = candidates.get(rr.index());
                return new RetrievalResult(
                    original.chunkId(),
                    original.documentId(),
                    original.content(),
                    original.title(),
                    original.sectionPath(),
                    original.pageNumber(),
                    rr.score(),
                    original.bm25Score(),
                    original.vectorScore(),
                    original.sourceLabels()
                );
            })
            .toList();
    }
}
