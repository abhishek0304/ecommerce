package com.ecommerce.order_service.shipping;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration @EnableScheduling
@ConditionalOnProperty(name="shipping.tracking.enabled",havingValue="true")
public class ShippingSchedule {
    private final ShipmentRepository shipments;
    private final TransactionTemplate tx;
    private final ShippingService shipping;
    public ShippingSchedule(ShipmentRepository shipments,TransactionTemplate tx,ShippingService shipping) {
        this.shipments=shipments; this.tx=tx; this.shipping=shipping;
    }
    @Scheduled(fixedDelayString="${shipping.tracking.delay-ms:60000}")
    public void refresh() {
        for(String id:shipments.due(Instant.now(),ShippingDtos.Mode.MOCK,PageRequest.of(0,25))) {
            Shipment row=tx.execute(t -> {
                Shipment s=shipments.lock(id).orElseThrow();
                if(s.nextTrackAt.isAfter(Instant.now())) return null;
                s.nextTrackAt=Instant.now().plusSeconds(900); return s;
            });
            if(row!=null) {
                try { shipping.refresh(row.orderId,row.direction); }
                catch(RuntimeException ignored) { /* Keep last good status; retry at the next due time. */ }
            }
        }
    }
}
