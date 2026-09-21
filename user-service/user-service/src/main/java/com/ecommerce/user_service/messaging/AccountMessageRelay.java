package com.ecommerce.user_service.messaging;
import java.time.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import feign.FeignException;
@Service
public class AccountMessageRelay {
    private final AccountMessageRepository messages;private final TransactionTemplate tx;private final NotificationApi client;
    private final String key;
    public AccountMessageRelay(AccountMessageRepository messages,TransactionTemplate tx,NotificationApi client,
            @Value("${internal.service-key:local-development-service-key-change-me}") String key) {
        this.messages=messages;this.tx=tx;
        this.client=client;this.key=key;
    }
    public void run() {
        for(String id:messages.due(Instant.now(),PageRequest.of(0,50))) {
            AccountMessage m=tx.execute(s -> {var row=messages.lock(id).orElseThrow();
                if(!row.status.equals("PENDING") || row.nextAttemptAt.isAfter(Instant.now())) return null;
                if(!row.expiresAt.isAfter(Instant.now())) {finish(row,"EXPIRED");return null;}
                row.attempts++;row.nextAttemptAt=Instant.now().plusSeconds(30);return row;
            });
            if(m==null) continue;
            try {
                var response=client.enqueue(id,key,Map.of("userId",m.userId,"channel","EMAIL","recipient",m.recipient,
                    "subject",m.subject,"body",m.body,"expiresAt",m.expiresAt.toString()));
                if(response.getStatusCode().value()!=202) continue;
                tx.executeWithoutResult(s -> finish(messages.lock(id).orElseThrow(),"QUEUED"));
            } catch(FeignException ex) {
                // The stable message ID makes retry safe even if notification-service committed before a timeout.
                // Do not log exception bodies: the request contains an OTP and an email address.
            }
        }
    }
    private static void finish(AccountMessage m,String status) {m.status=status;m.body="";m.recipient=null;}
}
