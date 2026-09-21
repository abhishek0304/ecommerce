package com.ecommerce.notification_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 3, topics = {"order-events"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class NotificationTests {
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired NotificationRepository notifications;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    String token(long user) {
        return "Bearer " + Jwts.builder().subject("user" + user + "@example.com").claim("userId", user)
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("change-this-development-secret-key-to-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
    @Test void kafkaDeliveryPersistsOneNotificationPerEventAndKeepsUserInboxesPrivate() throws Exception {
        String orderId = UUID.randomUUID().toString(), eventId = UUID.randomUUID().toString();
        String payload = json.writeValueAsString(Map.of("schemaVersion", 1, "eventId", eventId, "orderId", orderId,
                "userId", 1, "sequence", 1, "type", "OrderShipped", "occurredAt", Instant.now().toString(),
                "carrier", "Test Carrier", "trackingNumber", "TRACK123"));
        kafka.send("order-events", orderId, payload).get(15, TimeUnit.SECONDS);
        kafka.send("order-events", orderId, payload).get(15, TimeUnit.SECONDS);
        String second = json.writeValueAsString(Map.of("schemaVersion", 1, "eventId", UUID.randomUUID().toString(), "orderId", orderId,
                "userId", 1, "sequence", 2, "type", "OrderDelivered", "occurredAt", Instant.now().toString()));
        kafka.send("order-events", orderId, second).get(15, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(notifications.count()).isEqualTo(2));
        assertThat(notifications.findById(eventId).orElseThrow().message).contains("TRACK123");
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications").header("Authorization", token(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/notifications?userId=1").header("Authorization", token(2))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        for (String type : List.of("ReturnRequested", "ReturnApproved", "ReturnRejected", "ReturnReceived")) {
            String returnEvent = json.writeValueAsString(Map.of("schemaVersion", 1, "eventId", UUID.randomUUID().toString(), "orderId", orderId,
                    "userId", 1, "sequence", 3, "type", type, "occurredAt", Instant.now().toString()));
            kafka.send("order-events", orderId, returnEvent).get(15, TimeUnit.SECONDS);
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(notifications.count()).isEqualTo(6));
    }
}
