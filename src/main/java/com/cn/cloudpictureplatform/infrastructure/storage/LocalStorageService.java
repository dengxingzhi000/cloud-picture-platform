package com.cn.cloudpictureplatform.infrastructure.storage;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.config.StorageProperties;
import com.cn.cloudpictureplatform.domain.storage.StorageResult;
import com.cn.cloudpictureplatform.domain.storage.StorageService;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {
    private final StorageProperties storageProperties;

    public LocalStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public StorageResult store(MultipartFile file, String key) {
        Path root = Paths.get(storageProperties.getLocal().getRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid storage key");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new ApiException(ApiErrorCode.SERVER_ERROR, "failed to store file");
        }
        String url = "/api/files/" + key.replace("\\", "/");
        return new StorageResult(key, url, file.getSize(), file.getContentType());
    }

    @Override
    public StorageResult store(byte[] data, String key, String contentType) {
        Path root = Paths.get(storageProperties.getLocal().getRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid storage key");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException ex) {
            throw new ApiException(ApiErrorCode.SERVER_ERROR, "failed to store file");
        }
        String url = "/api/files/" + key.replace("\\", "/");
        return new StorageResult(key, url, data.length, contentType);
    }

    @Override
    public InputStream retrieve(String key) {
        Path root = Paths.get(storageProperties.getLocal().getRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid storage key");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException ex) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "file not found: " + key);
        }
    }

    @Override
    public boolean delete(String key) {
        Path root = Paths.get(storageProperties.getLocal().getRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            return false;
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException ex) {
            return false;
        }
    }
}
