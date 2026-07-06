package com.cn.cloudpictureplatform.rag.application;

import com.cn.cloudpictureplatform.rag.domain.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final RetrievalService retrievalService;
    private final RerankService rerankService;
    private final ObjectMapper objectMapper;

    public EvaluationResult runEvaluation() {
        try {
            GoldenSetEntry[] entries = objectMapper.readValue(
                new ClassPathResource("golden_set.json").getInputStream(),
                GoldenSetEntry[].class
            );

            int totalQueries = entries.length;
            int recallAt5Hits = 0;
            int rerankShiftCount = 0;

            for (GoldenSetEntry entry : entries) {
                List<RetrievalResult> retrieved = retrievalService.retrieve(entry.query());
                List<RetrievalResult> reranked = rerankService.rerank(entry.query(), retrieved);

                Set<String> expectedDocs = Set.of(entry.expectedDocuments());
                boolean found = reranked.stream()
                    .limit(5)
                    .anyMatch(r -> expectedDocs.contains(r.title()));
                if (found) recallAt5Hits++;

                if (!retrieved.isEmpty() && !reranked.isEmpty()) {
                    String topBefore = retrieved.get(0).chunkId();
                    String topAfter = reranked.get(0).chunkId();
                    if (!topBefore.equals(topAfter)) rerankShiftCount++;
                }
            }

            return new EvaluationResult(
                totalQueries,
                (double) recallAt5Hits / totalQueries,
                (double) rerankShiftCount / totalQueries
            );

        } catch (Exception e) {
            log.error("Evaluation failed: {}", e.getMessage());
            return new EvaluationResult(0, 0.0, 0.0);
        }
    }

    public record EvaluationResult(int totalQueries, double recallAt5, double rerankShiftRate) {}
    public record GoldenSetEntry(String query, String[] expectedDocuments, String[] expectedSections) {}
}
