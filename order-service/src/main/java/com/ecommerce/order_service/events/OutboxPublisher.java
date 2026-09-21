package com.ecommerce.order_service.events;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Component
@ConditionalOnProperty(name = "order.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository events;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final OutboxTracing tracing;
    public OutboxPublisher(OutboxRepository events, KafkaTemplate<String, String> kafka, PlatformTransactionManager manager, OutboxTracing tracing) {
        this.events = events; this.kafka = kafka; this.tx = new TransactionTemplate(manager);
        this.tracing = tracing;
    }
    @Scheduled(fixedDelayString = "${order.outbox.delay-ms:5000}", initialDelayString = "${order.outbox.delay-ms:5000}")
    public void publish() {
        for (int i = 0; i < 50 && publishNext(); i++) { /* bounded batches */ }
    }
    public boolean publishNext() {
        try {
            return Boolean.TRUE.equals(tx.execute(status -> {
                var pending = events.pending(PageRequest.of(0, 1));
                if (pending.isEmpty()) return false;
                var event = pending.getFirst();
                tracing.publish(event, () -> {
                    try { kafka.send("order-events", event.orderId, event.payload).get(10, TimeUnit.SECONDS); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                    catch (Exception ex) { throw new IllegalStateException(ex); }
                });
                event.publishedAt = Instant.now();
                return true;
            }));
        } catch (RuntimeException ex) {
            log.warn("Order event delivery is pending; the outbox will retry");
            return false;
        }
    }
}
