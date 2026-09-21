package com.ecommerce.notification_service;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
@Configuration
public class KafkaConfig {
    @Bean NewTopic orderEventsTopic() { return TopicBuilder.name("order-events").partitions(3).replicas(1).build(); }
    @Bean DefaultErrorHandler notificationErrorHandler() {
        // Keep the offset uncommitted on persistence failures; never silently discard a notification.
        var handler = new DefaultErrorHandler(new FixedBackOff(5000, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setClassifications(java.util.Map.of(Exception.class, true), true);
        return handler;
    }
}
