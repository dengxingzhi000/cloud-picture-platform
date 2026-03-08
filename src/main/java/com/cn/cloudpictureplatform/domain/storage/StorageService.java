package com.cn.cloudpictureplatform.domain.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    StorageResult store(MultipartFile file, String key);
}
