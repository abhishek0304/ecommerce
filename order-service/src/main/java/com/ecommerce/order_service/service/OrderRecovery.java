package com.ecommerce.order_service.service;
import com.ecommerce.order_service.model.OrderRepository;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name = "order.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class OrderRecovery {
    private static final Logger log = LoggerFactory.getLogger(OrderRecovery.class);
    private final OrderRepository orders;
    private final OrderService service;
    public OrderRecovery(OrderRepository orders, OrderService service) { this.orders = orders; this.service = service; }
    @Scheduled(fixedDelayString = "${order.recovery.delay-ms:30000}", initialDelayString = "${order.recovery.delay-ms:30000}")
    public void recover() {
        for (String id : orders.recoverable(PageRequest.of(0, 100))) {
            try { service.advance(id); }
            catch (RuntimeException ex) { log.warn("Order {} recovery could not complete; retrying later", id); }
        }
    }
}
