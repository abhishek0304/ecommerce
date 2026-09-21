package com.ecommerce.user_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
public class KafkaTopicConfig {
    public static final String USER_EVENTS = "user-events";

    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(USER_EVENTS).partitions(1).replicas(1).build();
    }
}
