package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenSearchIndexInitializer {

    private final OpenSearchClient openSearchClient;
    private final RagProperties ragProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndex() {
        try {
            var config = ragProperties.openSearch();
            boolean exists = openSearchClient.indices().exists(
                ExistsRequest.of(e -> e.index(config.indexName()))
            ).value();

            if (!exists) {
                openSearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(config.indexName())
                    .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                    )
                    .mappings(m -> m
                        .properties("documentId", p -> p.keyword(k -> k))
                        .properties("chunkIndex", p -> p.integer(i -> i))
                        .properties("content", p -> p.text(t -> t.analyzer("standard")))
                        .properties("tokenCount", p -> p.integer(i -> i))
                        .properties("title", p -> p.text(t -> t.analyzer("standard")))
                        .properties("sectionPath", p -> p.text(t -> t.analyzer("standard")))
                        .properties("pageNumber", p -> p.integer(i -> i))
                        .properties("bm25Boost", p -> p.float_(f -> f))
                        .properties("embeddingDim", p -> p.integer(i -> i))
                        .properties("openSearchId", p -> p.keyword(k -> k))
                        .properties("embedding", p -> p.knnVector(k -> k
                            .dimension(ragProperties.embedding().dimensions())
                        ))
                    )
                ));
                log.info("Created OpenSearch index: {}", config.indexName());
            } else {
                log.info("OpenSearch index already exists: {}", config.indexName());
            }
        } catch (Exception e) {
            log.warn("Failed to initialize OpenSearch index (will retry): {}", e.getMessage());
        }
    }
}