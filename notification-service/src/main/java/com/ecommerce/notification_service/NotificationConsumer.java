package com.ecommerce.notification_service;
import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class NotificationConsumer {
    private final NotificationRepository notifications;
    private final ObjectMapper json;
    private final com.ecommerce.notification_service.delivery.DeliveryQueue deliveries;
    public NotificationConsumer(NotificationRepository notifications, ObjectMapper json, com.ecommerce.notification_service.delivery.DeliveryQueue deliveries) { this.notifications = notifications; this.json = json; this.deliveries=deliveries; }
    @KafkaListener(topics = "order-events", groupId = "ecommerce-notifications")
    @Transactional
    public void receive(String payload) {
        try {
            JsonNode event = json.readTree(payload);
            String eventId = UUID.fromString(event.path("eventId").asText()).toString();
            if (notifications.existsById(eventId)) return;
            String orderId = UUID.fromString(event.path("orderId").asText()).toString();
            long userId = event.path("userId").asLong(-1), sequence = event.path("sequence").asLong(-1);
            if (event.path("schemaVersion").asInt() != 1 || userId <= 0 || sequence <= 0) throw new IllegalArgumentException("Invalid event envelope");
            String type = event.path("type").asText();
            String description = switch (type) {
                case "OrderConfirmed" -> "Your order is confirmed.";
                case "OrderFailed" -> "Your order could not be placed. Check the order for details.";
                case "PaymentPending" -> "Your order is awaiting online payment.";
                case "PaymentCaptured" -> "Your payment was received.";
                case "PaymentCollected" -> "Your cash-on-delivery payment was collected.";
                case "OrderExpired" -> "Your payment window expired and the order was closed.";
                case "OrderCancelled" -> "Your order was cancelled.";
                case "RefundRequested" -> "A refund has been requested for your order.";
                case "RefundPending" -> "Your refund is being processed.";
                case "RefundProcessed" -> "Your refund was processed.";
                case "RefundFailed" -> "Your refund needs attention. Please contact support.";
                case "OrderProcessing" -> "Your order is being prepared.";
                case "OrderShipped" -> "Your order has shipped. Carrier: " + event.path("carrier").asText("") + ", tracking: " + event.path("trackingNumber").asText("");
                case "OrderDelivered" -> "Your order was delivered.";
                case "ReturnRequested" -> "Your return request has been received.";
                case "ReturnApproved" -> "Your return request was approved. Arrange return delivery with the store.";
                case "ReturnRejected" -> "Your return request was declined. See your order for the reason.";
                case "ReturnReceived" -> "Your returned items were received. Your refund is being arranged.";
                default -> null;
            };
            // Future event types do not block this version of the notification consumer.
            if (description == null) return;
            Notification notification = new Notification();
            notification.eventId = eventId; notification.userId = userId; notification.orderId = orderId;
            notification.eventSequence = sequence; notification.type = type; notification.message = description;
            notification.occurredAt = Instant.parse(event.path("occurredAt").asText());
            notifications.saveAndFlush(notification);
            deliveries.order(eventId,userId,orderId,type,description);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid order event JSON", ex);
        }
    }
}
