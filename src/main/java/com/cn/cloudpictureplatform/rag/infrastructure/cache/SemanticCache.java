package com.cn.cloudpictureplatform.rag.infrastructure.cache;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Component
public class SemanticCache {

    private final Deque<CachedEntry> cache = new ConcurrentLinkedDeque<>();
    private final double threshold;
    private final int maxEntries;
    private final long ttlMs;

    public SemanticCache(RagProperties ragProperties) {
        this.threshold = ragProperties.cache().semanticThreshold();
        this.maxEntries = ragProperties.cache().maxEntries();
        this.ttlMs = ragProperties.cache().ttlMs();
    }

    public Optional<String> findCachedAnswer(List<Float> queryEmbedding) {
        long now = System.currentTimeMillis();
        for (CachedEntry entry : cache) {
            if (now - entry.timestamp() > ttlMs) {
                continue;
            }
            double similarity = cosineSimilarity(queryEmbedding, entry.embedding());
            if (similarity >= threshold) {
                log.debug("Semantic cache hit: similarity={}", similarity);
                return Optional.of(entry.answer());
            }
        }
        return Optional.empty();
    }

    public void cacheAnswer(List<Float> queryEmbedding, String answer) {
        evictExpired();
        cache.addLast(new CachedEntry(queryEmbedding, answer, System.currentTimeMillis()));
        while (cache.size() > maxEntries) {
            cache.pollFirst();
        }
    }

    public void evictExpired() {
        long now = System.currentTimeMillis();
        cache.removeIf(entry -> now - entry.timestamp() > ttlMs);
    }

    private double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record CachedEntry(List<Float> embedding, String answer, long timestamp) {}
}
