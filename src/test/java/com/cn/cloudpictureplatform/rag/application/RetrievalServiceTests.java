package com.cn.cloudpictureplatform.rag.application;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RetrievalServiceTests {

    @Test
    void shouldComputeRRFCorrectly() {
        List<String> bm25Results = List.of("doc1", "doc2", "doc3");
        List<String> vectorResults = List.of("doc2", "doc1", "doc4");

        Map<String, Double> rrfScores = computeRRF(bm25Results, vectorResults, 60);

        assertEquals(rrfScores.get("doc1"), rrfScores.get("doc2"), 0.0001);
        assertTrue(rrfScores.containsKey("doc3"));
        assertTrue(rrfScores.containsKey("doc4"));
    }

    private Map<String, Double> computeRRF(List<String> listA, List<String> listB, int k) {
        Map<String, Double> scores = new HashMap<>();
        for (int i = 0; i < listA.size(); i++) {
            scores.merge(listA.get(i), 1.0 / (k + i + 1), Double::sum);
        }
        for (int i = 0; i < listB.size(); i++) {
            scores.merge(listB.get(i), 1.0 / (k + i + 1), Double::sum);
        }
        return scores;
    }
}
