package com.cn.cloudpictureplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SearchIndexConfig {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexConfig.class);

    @Bean(name = "searchIndexTaskExecutor")
    public TaskExecutor searchIndexTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("search-index-");
        executor.setRejectedExecutionHandler((runnable, pool) ->
                log.warn("Search index queue is full (capacity=500). Task rejected: {}", runnable)
        );
        executor.initialize();
        return executor;
    }
}
