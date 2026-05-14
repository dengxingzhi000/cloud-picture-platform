package com.cn.cloudpictureplatform.infrastructure.ai.consumer;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.connection.ConnectionFactory")
public class AiQueueConfig {

    public static final String AI_EXCHANGE = "ai.exchange";
    public static final String TAGGING_RESULT_QUEUE = "ai.tagging.result";
    public static final String MODERATION_RESULT_QUEUE = "ai.moderation.result";
    public static final String TAGGING_RESULT_KEY = "ai.tagging.result";
    public static final String MODERATION_RESULT_KEY = "ai.moderation.result";

    @Bean
    public TopicExchange aiExchange() {
        return new TopicExchange(AI_EXCHANGE, true, false);
    }

    @Bean
    public Queue taggingResultQueue() {
        return new Queue(TAGGING_RESULT_QUEUE, true);
    }

    @Bean
    public Queue moderationResultQueue() {
        return new Queue(MODERATION_RESULT_QUEUE, true);
    }

    @Bean
    public Binding taggingBinding(TopicExchange aiExchange, Queue taggingResultQueue) {
        return BindingBuilder.bind(taggingResultQueue).to(aiExchange).with(TAGGING_RESULT_KEY);
    }

    @Bean
    public Binding moderationBinding(TopicExchange aiExchange, Queue moderationResultQueue) {
        return BindingBuilder.bind(moderationResultQueue).to(aiExchange).with(MODERATION_RESULT_KEY);
    }
}
