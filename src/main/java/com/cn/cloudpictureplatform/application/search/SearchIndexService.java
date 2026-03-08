package com.cn.cloudpictureplatform.application.search;

import java.util.UUID;

public interface SearchIndexService {
    void enqueuePicture(UUID pictureId);
}
