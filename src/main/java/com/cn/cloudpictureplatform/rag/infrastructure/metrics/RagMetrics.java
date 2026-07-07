package com.cn.cloudpictureplatform.rag.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RagMetrics {

    private final MeterRegistry meterRegistry;

    private Counter ingestionDocumentsCounter;
    private Counter ingestionChunksCounter;
    private Timer ingestionDurationTimer;
    private Timer retrievalLatencyTimer;
    private Counter retrievalCacheHitCounter;
    private Timer generationLatencyTimer;

    @PostConstruct
    public void init() {
        ingestionDocumentsCounter = Counter.builder("rag_ingestion_documents_total").register(meterRegistry);
        ingestionChunksCounter = Counter.builder("rag_ingestion_chunks_total").register(meterRegistry);
        ingestionDurationTimer = Timer.builder("rag_ingestion_duration_seconds").register(meterRegistry);
        retrievalLatencyTimer = meterRegistry.timer("rag_retrieval_total_latency_ms");
        retrievalCacheHitCounter = Counter.builder("rag_retrieval_cache_hit_total").register(meterRegistry);
        generationLatencyTimer = meterRegistry.timer("rag_generation_latency_ms");
    }

    public void recordIngestion(int chunksCount, long durationMs) {
        ingestionDocumentsCounter.increment();
        ingestionChunksCounter.increment(chunksCount);
        ingestionDurationTimer.record(Duration.ofMillis(durationMs));
    }

    public void recordRetrieval(long totalMs, boolean cacheHit) {
        retrievalLatencyTimer.record(Duration.ofMillis(totalMs));
        if (cacheHit) {
            retrievalCacheHitCounter.increment();
        }
    }

    public void recordGeneration(long latencyMs) {
        generationLatencyTimer.record(Duration.ofMillis(latencyMs));
    }
}
