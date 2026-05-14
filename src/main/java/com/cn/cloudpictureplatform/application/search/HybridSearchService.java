package com.cn.cloudpictureplatform.application.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.cn.cloudpictureplatform.application.shared.dto.PictureSummary;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.infrastructure.ai.gateway.AiGateway;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureSearchDocumentRepository;

@Service
public class HybridSearchService {
    private final AiGateway aiGateway;
    private final PictureSearchDocumentRepository searchDocumentRepository;

    public HybridSearchService(
            @Autowired(required = false) AiGateway aiGateway,
            PictureSearchDocumentRepository searchDocumentRepository
    ) {
        this.aiGateway = aiGateway;
        this.searchDocumentRepository = searchDocumentRepository;
    }

    public PageResponse<PictureSummary> search(String query, int page, int size) {
        if (!StringUtils.hasText(query)) {
            return new PageResponse<>(List.of(), 0, page, size);
        }
        int pageSize = Math.min(Math.max(1, size), 100);
        int fetchK = (page + 1) * pageSize * 2;

        List<UUID> semanticIds = searchSemantic(query, fetchK);
        List<UUID> keywordIds = searchKeyword(query, fetchK);

        List<UUID> merged = reciprocalRankFusion(semanticIds, keywordIds, 60);
        int total = merged.size();
        int start = page * pageSize;
        int end = Math.min(start + pageSize, merged.size());
        List<UUID> pageIds = start < merged.size() ? merged.subList(start, end) : List.of();
        var assets = searchDocumentRepository.findPictureAssetsByIds(pageIds);
        var items = assets.stream()
                .map(a -> new PictureSummary(a.getId(), a.getName(), a.getUrl(),
                        a.getVisibility(), a.getSizeBytes(), a.getWidth(), a.getHeight()))
                .collect(Collectors.toList());
        return new PageResponse<>(items, total, page, pageSize);
    }

    private List<UUID> searchSemantic(String query, int limit) {
        if (aiGateway == null) return List.of();
        try {
            float[] vector = aiGateway.embedText(query).orElse(null);
            if (vector == null) return List.of();
            return searchDocumentRepository.findByVectorDistance(toPgVector(vector), limit);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<UUID> searchKeyword(String query, int limit) {
        var pageable = PageRequest.of(0, limit);
        return searchDocumentRepository.findByContentContainingIgnoreCase(query, pageable)
                .stream()
                .map(doc -> doc.getPictureId())
                .toList();
    }

    static List<UUID> reciprocalRankFusion(List<UUID> listA, List<UUID> listB, int k) {
        Map<UUID, Double> scores = new HashMap<>();
        for (int i = 0; i < listA.size(); i++) {
            scores.merge(listA.get(i), 1.0 / (k + i + 1), Double::sum);
        }
        for (int i = 0; i < listB.size(); i++) {
            scores.merge(listB.get(i), 1.0 / (k + i + 1), Double::sum);
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String toPgVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
