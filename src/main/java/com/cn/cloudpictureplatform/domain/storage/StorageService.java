package com.cn.cloudpictureplatform.domain.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    StorageResult store(MultipartFile file, String key);

    /**
     * 删除存储的文件（用于清理孤儿文件）
     * @param key 存储键
     * @return 是否删除成功
     */
    boolean delete(String key);
}
