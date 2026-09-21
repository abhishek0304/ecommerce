package com.ecommerce.user_service.messaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
@Configuration @EnableScheduling
@ConditionalOnProperty(name="messages.worker.enabled",havingValue="true",matchIfMissing=true)
public class AccountMessageSchedule {
    private final AccountMessageRelay relay;
    public AccountMessageSchedule(AccountMessageRelay relay) {this.relay=relay;}
    @Scheduled(fixedDelayString="${messages.worker.delay-ms:5000}") public void send() {relay.run();}
}
