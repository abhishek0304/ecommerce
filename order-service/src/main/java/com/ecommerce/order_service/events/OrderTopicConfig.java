package com.ecommerce.order_service.events;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
@Configuration
@ConditionalOnProperty(name = "order.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OrderTopicConfig {
    @Bean NewTopic orderEventsTopic() { return TopicBuilder.name("order-events").partitions(3).replicas(1).build(); }
}
