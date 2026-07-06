package com.cn.cloudpictureplatform.rag.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RagMetrics {

    private final MeterRegistry meterRegistry;

    public void recordIngestion(int chunksCount, long durationMs) {
        Counter.builder("rag_ingestion_documents_total").register(meterRegistry).increment();
        Counter.builder("rag_ingestion_chunks_total").register(meterRegistry).increment(chunksCount);
        Timer.builder("rag_ingestion_duration_seconds").register(meterRegistry).record(Duration.ofMillis(durationMs));
    }

    public void recordRetrieval(long totalMs, boolean cacheHit) {
        meterRegistry.timer("rag_retrieval_total_latency_ms").record(Duration.ofMillis(totalMs));
        if (cacheHit) {
            Counter.builder("rag_retrieval_cache_hit_total").register(meterRegistry).increment();
        }
    }

    public void recordGeneration(long latencyMs) {
        meterRegistry.timer("rag_generation_latency_ms").record(Duration.ofMillis(latencyMs));
    }
}
