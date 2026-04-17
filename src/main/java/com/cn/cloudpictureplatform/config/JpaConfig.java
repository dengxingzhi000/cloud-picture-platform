package com.cn.cloudpictureplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@EnableJpaAuditing
public class JpaConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
