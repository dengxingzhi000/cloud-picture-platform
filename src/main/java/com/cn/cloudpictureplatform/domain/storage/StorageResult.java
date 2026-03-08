package com.cn.cloudpictureplatform.domain.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StorageResult {

    private String key;
    private String url;
    private long sizeBytes;
    private String contentType;
}
