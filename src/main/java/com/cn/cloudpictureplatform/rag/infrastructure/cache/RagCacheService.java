package com.cn.cloudpictureplatform.rag.infrastructure.cache;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagCacheService {

    private final RagProperties ragProperties;

    public Optional<List<Float>> getCachedEmbedding(String contentHash) {
        return Optional.empty();
    }

    public Optional<List<String>> getCachedRetrieval(String queryHash) {
        return Optional.empty();
    }
}
