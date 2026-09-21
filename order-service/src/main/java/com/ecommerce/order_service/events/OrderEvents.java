package com.ecommerce.order_service.events;
import com.ecommerce.order_service.model.PurchaseOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
@Component
public class OrderEvents {
    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final OutboxTracing tracing;
    public OrderEvents(OutboxRepository outbox, ObjectMapper json, OutboxTracing tracing) {
        this.outbox = outbox; this.json = json; this.tracing = tracing;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(PurchaseOrder order, String type) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = UUID.randomUUID().toString(); event.orderId = order.id;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1); payload.put("eventId", event.eventId);
        payload.put("orderId", order.id); payload.put("userId", order.userId);
        payload.put("sequence", ++order.eventSequence); payload.put("type", type);
        payload.put("occurredAt", Instant.now().toString()); payload.put("status", order.status);
        payload.put("paymentStatus", order.paymentStatus); payload.put("refundStatus", order.refundStatus);
        payload.put("carrier", order.carrier); payload.put("trackingNumber", order.trackingNumber);
        try { event.payload = json.writeValueAsString(payload); }
        catch (Exception ex) { throw new IllegalStateException("Cannot serialize order event", ex); }
        tracing.capture(event);
        outbox.save(event);
    }
}
