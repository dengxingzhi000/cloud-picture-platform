package com.cn.cloudpictureplatform.rag.infrastructure.opensearch;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenSearchChunkClient {

    private final OpenSearchClient openSearchClient;
    private final RagProperties ragProperties;

    public String indexChunk(ChunkDocument chunk) {
        try {
            var config = ragProperties.openSearch();
            IndexRequest<ChunkDocument> request = IndexRequest.of(b -> b
                .index(config.indexName())
                .id(chunk.openSearchId())
                .document(chunk)
            );

            IndexResponse response = openSearchClient.index(request);
            log.debug("Indexed chunk {} into OpenSearch: result={}",
                chunk.openSearchId(), response.result());
            return response.id();
        } catch (Exception e) {
            throw new RuntimeException("Failed to index chunk into OpenSearch", e);
        }
    }

    public void deleteByDocumentId(String documentId) {
        try {
            var config = ragProperties.openSearch();
            openSearchClient.deleteByQuery(d -> d
                .index(config.indexName())
                .query(q -> q
                    .term(t -> t
                        .field("documentId")
                        .value(FieldValue.of(documentId))
                    )
                )
            );
            log.debug("Deleted chunks for document {} from OpenSearch", documentId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete chunks from OpenSearch", e);
        }
    }
}