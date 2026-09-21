package com.ecommerce.notification_service.delivery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
@Configuration @EnableScheduling
@ConditionalOnProperty(name="messages.worker.enabled",havingValue="true",matchIfMissing=true)
public class DeliverySchedule {
    private final DeliveryWorker worker;
    public DeliverySchedule(DeliveryWorker worker) {this.worker=worker;}
    @Scheduled(fixedDelayString="${messages.worker.delay-ms:5000}") public void send() {worker.run();}
}
