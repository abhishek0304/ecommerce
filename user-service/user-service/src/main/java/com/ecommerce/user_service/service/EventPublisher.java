package com.ecommerce.user_service.service;
import com.ecommerce.user_service.config.KafkaTopicConfig;
import org.springframework.kafka.core.KafkaTemplate; import org.springframework.stereotype.Service; import java.time.*; import java.util.*;

@Service
public class EventPublisher {
	private final Optional<KafkaTemplate<String, Object>> kafka;

	public EventPublisher(Optional<KafkaTemplate<String, Object>> kafka) {
		this.kafka = kafka;
	}

	public void publish(String type, Long userId, String email) {
		kafka.ifPresent(k -> k.send(KafkaTopicConfig.USER_EVENTS, userId.toString(),
				Map.of("type", type, "userId", userId, "email", email, "occurredAt", Instant.now().toString())));
	}
}
