package com.cn.cloudpictureplatform.infrastructure.search;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.cn.cloudpictureplatform.application.search.SearchIndexService;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.PictureTag;
import com.cn.cloudpictureplatform.domain.search.PictureSearchDocument;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureSearchDocumentRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureTagRepository;

@Slf4j
@Service
public class DatabaseSearchIndexService implements SearchIndexService {
    private final TaskExecutor searchIndexTaskExecutor;
    private final PictureAssetRepository pictureAssetRepository;
    private final PictureTagRepository pictureTagRepository;
    private final PictureSearchDocumentRepository pictureSearchDocumentRepository;
    private final Counter successCounter;
    private final Counter enqueueCounter;

    public DatabaseSearchIndexService(
            @Qualifier("searchIndexTaskExecutor") TaskExecutor searchIndexTaskExecutor,
            PictureAssetRepository pictureAssetRepository,
            PictureTagRepository pictureTagRepository,
            PictureSearchDocumentRepository pictureSearchDocumentRepository,
            MeterRegistry meterRegistry
    ) {
        this.searchIndexTaskExecutor = searchIndexTaskExecutor;
        this.pictureAssetRepository = pictureAssetRepository;
        this.pictureTagRepository = pictureTagRepository;
        this.pictureSearchDocumentRepository = pictureSearchDocumentRepository;
        this.successCounter = meterRegistry.counter("search.index.success");
        this.enqueueCounter = meterRegistry.counter("search.index.enqueued");
    }

    @Override
    public void enqueuePicture(UUID pictureId) {
        if (pictureId == null) {
            return;
        }
        enqueueCounter.increment();
        searchIndexTaskExecutor.execute(() -> indexWithRetry(pictureId));
    }

    private void indexPicture(UUID pictureId) {
        PictureAsset asset = pictureAssetRepository.findById(pictureId).orElse(null);
        if (asset == null) {
            pictureSearchDocumentRepository.deleteById(pictureId);
            return;
        }
        List<PictureTag> tags = pictureTagRepository.findByPictureAssetId(pictureId);
        String content = buildContent(asset, tags);
        PictureSearchDocument document = pictureSearchDocumentRepository.findById(pictureId)
                .orElseGet(() -> PictureSearchDocument.builder().pictureId(pictureId).build());
        document.setContent(content);
        document.setUpdatedAt(Instant.now());
        pictureSearchDocumentRepository.save(document);
        successCounter.increment();
    }

    private void indexWithRetry(UUID pictureId) {
        indexPicture(pictureId);
    }

    private String buildContent(PictureAsset asset, List<PictureTag> tags) {
        List<String> tokens = new ArrayList<>();
        addToken(tokens, asset.getName());
        addToken(tokens, asset.getOriginalFilename());
        if (tags != null) {
            for (PictureTag tag : tags) {
                addToken(tokens, tag.getTagText());
            }
        }
        if (tokens.isEmpty()) {
            tokens.add(asset.getId().toString());
        }
        return tokens.stream().distinct().collect(Collectors.joining(" "));
    }

    private void addToken(List<String> tokens, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        tokens.add(value.trim().toLowerCase());
    }
}
