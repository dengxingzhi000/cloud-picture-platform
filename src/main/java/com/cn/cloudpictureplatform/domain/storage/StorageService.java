package com.cn.cloudpictureplatform.domain.storage;

import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    StorageResult store(MultipartFile file, String key);

    StorageResult store(byte[] data, String key, String contentType);

    InputStream retrieve(String key);

    boolean delete(String key);
}
