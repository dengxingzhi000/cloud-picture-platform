package com.cn.cloudpictureplatform.application.search;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;

@Service
public class SearchMaintenanceService {
    private static final int DEFAULT_PAGE_SIZE = 500;

    private final PictureAssetRepository pictureAssetRepository;
    private final SearchIndexService searchIndexService;

    public SearchMaintenanceService(
            PictureAssetRepository pictureAssetRepository,
            SearchIndexService searchIndexService
    ) {
        this.pictureAssetRepository = pictureAssetRepository;
        this.searchIndexService = searchIndexService;
    }

    public int enqueueAllPictures() {
        int total = 0;
        int page = 0;
        Page<PictureAsset> result;
        do {
            result = pictureAssetRepository.findAll(PageRequest.of(page, DEFAULT_PAGE_SIZE));
            for (PictureAsset asset : result.getContent()) {
                UUID pictureId = asset.getId();
                searchIndexService.enqueuePicture(pictureId);
                total += 1;
            }
            page += 1;
        } while (result.hasNext());
        return total;
    }
}
