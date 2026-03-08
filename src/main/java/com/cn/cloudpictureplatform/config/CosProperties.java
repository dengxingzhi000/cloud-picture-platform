package com.cn.cloudpictureplatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage.cos")
public class CosProperties {

    private String secretId;
    private String secretKey;
    private String region;
    private String bucket;
    private String endpoint;
    private String baseUrl;
    private String prefix = "";
}
