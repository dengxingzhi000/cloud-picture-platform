package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.config.RagProperties;
import com.cn.cloudpictureplatform.rag.domain.RetrievalResult;
import com.cn.cloudpictureplatform.rag.infrastructure.cache.SemanticCache;
import com.cn.cloudpictureplatform.rag.infrastructure.embedding.EmbeddingClient;
import com.cn.cloudpictureplatform.rag.infrastructure.generator.GenerationClient;
import com.cn.cloudpictureplatform.rag.infrastructure.metrics.RagMetrics;
import com.cn.cloudpictureplatform.rag.infrastructure.persistence.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QaServiceTests {

    @Mock
    private RetrievalService retrievalService;
    @Mock
    private RerankService rerankService;
    @Mock
    private GenerationClient generationClient;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private QueryRewriter queryRewriter;
    @Mock
    private SemanticCache semanticCache;
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private RagProperties ragProperties;
    @Mock
    private RagMetrics ragMetrics;

    @InjectMocks
    private QaService qaService;

    @Test
    void shouldReturnCachedAnswerOnCacheHit() {
        when(queryRewriter.rewrite(eq("test"), anyList())).thenReturn("test");
        when(ragProperties.embedding()).thenReturn(
            new RagProperties.Embedding("http://localhost", "model", 1024, 32, java.time.Duration.ofSeconds(10))
        );
        when(embeddingClient.embedSingle("test", 1024)).thenReturn(List.of(0.1f, 0.2f));
        when(semanticCache.findCachedAnswer(List.of(0.1f, 0.2f))).thenReturn(Optional.of("cached answer"));

        QaService.QaResponse response = qaService.ask("test");

        assertEquals("cached answer", response.answer());
        assertEquals(List.of("cached"), response.citations());
        verify(retrievalService, never()).retrieve(anyString(), anyInt(), anyList());
        verify(generationClient, never()).generate(anyString(), anyString());
    }

    @Test
    void shouldCallRetrievalWithPrecomputedEmbedding() {
        List<Float> embedding = List.of(0.1f, 0.2f);
        when(queryRewriter.rewrite(eq("test"), anyList())).thenReturn("test");
        when(ragProperties.embedding()).thenReturn(
            new RagProperties.Embedding("http://localhost", "model", 1024, 32, java.time.Duration.ofSeconds(10))
        );
        when(embeddingClient.embedSingle("test", 1024)).thenReturn(embedding);
        when(semanticCache.findCachedAnswer(embedding)).thenReturn(Optional.empty());
        when(retrievalService.retrieve("test", 0, embedding)).thenReturn(List.of());
        when(rerankService.rerank(eq("test"), anyList())).thenReturn(List.of());
        when(generationClient.generate(anyString(), anyString())).thenReturn("generated answer");

        QaService.QaResponse response = qaService.ask("test");

        verify(retrievalService).retrieve("test", 0, embedding);
        assertEquals("generated answer", response.answer());
    }

    @Test
    void shouldUseGenerationClientInterface() {
        List<Float> embedding = List.of(0.1f, 0.2f);
        when(queryRewriter.rewrite(eq("test"), anyList())).thenReturn("test");
        when(ragProperties.embedding()).thenReturn(
            new RagProperties.Embedding("http://localhost", "model", 1024, 32, java.time.Duration.ofSeconds(10))
        );
        when(embeddingClient.embedSingle("test", 1024)).thenReturn(embedding);
        when(semanticCache.findCachedAnswer(embedding)).thenReturn(Optional.empty());
        when(retrievalService.retrieve("test", 0, embedding)).thenReturn(List.of());
        when(rerankService.rerank(eq("test"), anyList())).thenReturn(List.of());
        when(generationClient.generate(anyString(), anyString())).thenReturn("answer");

        qaService.ask("test");

        verify(generationClient).generate(anyString(), anyString());
    }
}
