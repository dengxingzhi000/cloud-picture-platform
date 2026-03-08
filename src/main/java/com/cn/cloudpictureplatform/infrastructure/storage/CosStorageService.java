package com.cn.cloudpictureplatform.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.config.CosProperties;
import com.cn.cloudpictureplatform.domain.storage.StorageResult;
import com.cn.cloudpictureplatform.domain.storage.StorageService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "cos")
public class CosStorageService implements StorageService {
    private final COSClient cosClient;
    private final String bucket;
    private final String baseUrl;
    private final String prefix;

    public CosStorageService(CosProperties properties) {
        validateRequired(properties.getSecretId(), "secret-id");
        validateRequired(properties.getSecretKey(), "secret-key");
        validateRequired(properties.getRegion(), "region");
        validateRequired(properties.getBucket(), "bucket");
        COSCredentials credentials = new BasicCOSCredentials(
                properties.getSecretId(),
                properties.getSecretKey()
        );
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        this.cosClient = new COSClient(credentials, clientConfig);
        this.bucket = properties.getBucket();
        this.baseUrl = properties.getBaseUrl();
        this.prefix = normalizePrefix(properties.getPrefix());
    }

    @Override
    public StorageResult store(MultipartFile file, String key) {
        String objectKey = prefix + key;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        if (StringUtils.hasText(file.getContentType())) {
            metadata.setContentType(file.getContentType());
        }
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest request = new PutObjectRequest(bucket, objectKey, inputStream, metadata);
            cosClient.putObject(request);
        } catch (IOException ex) {
            throw new ApiException(ApiErrorCode.SERVER_ERROR, "failed to upload to cos");
        }
        String url = StringUtils.hasText(baseUrl)
                ? baseUrl + "/" + objectKey
                : cosClient.getObjectUrl(bucket, objectKey).toString();
        return new StorageResult(objectKey, url, file.getSize(), file.getContentType());
    }

    @PreDestroy
    public void shutdown() {
        cosClient.shutdown();
    }

    private void validateRequired(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("app.storage.cos." + name + " is required");
        }
    }

    private String normalizePrefix(String rawPrefix) {
        if (!StringUtils.hasText(rawPrefix)) {
            return "";
        }
        String prefixValue = rawPrefix.trim();
        if (!prefixValue.endsWith("/")) {
            prefixValue = prefixValue + "/";
        }
        return prefixValue;
    }
}
