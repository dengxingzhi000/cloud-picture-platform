package com.cn.cloudpictureplatform.rag.config;

import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean
    public OpenSearchClient openSearchClient(RagProperties properties) {
        var config = properties.openSearch();
        var restClient = RestClient.builder(
            org.apache.http.HttpHost.create(config.baseUrl())
        ).build();
        var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new OpenSearchClient(transport);
    }

    @Bean
    public WebClient qwenEmbeddingWebClient(RagProperties properties) {
        var config = properties.embedding();
        return WebClient.builder()
            .baseUrl(config.baseUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean
    public WebClient qwenRerankWebClient(RagProperties properties) {
        var config = properties.rerank();
        return WebClient.builder()
            .baseUrl(config.baseUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
