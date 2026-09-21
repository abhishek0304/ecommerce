package com.ecommerce.notification_service.delivery;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
@Service
public class DeliveryWorker {
    private final DeliveryRepository deliveries;private final TransactionTemplate tx;private final MessageTransport transport;private final RecipientClient recipients;
    public DeliveryWorker(DeliveryRepository deliveries,TransactionTemplate tx,MessageTransport transport,RecipientClient recipients) {
        this.deliveries=deliveries;this.tx=tx;this.transport=transport;this.recipients=recipients;
    }
    public void run() {for(String id:deliveries.due(Instant.now(),PageRequest.of(0,50))) process(id);}
    public void process(String id) {
        Delivery d=tx.execute(s -> {
            Delivery row=deliveries.lock(id).orElse(null);
            if(row==null || row.nextAttemptAt.isAfter(Instant.now())) return null;
            if(row.status.equals("SENDING")) {finish(row,"UNKNOWN","Worker stopped during delivery");return null;}
            if(!row.status.equals("PENDING")) return null;
            if(!row.expiresAt.isAfter(Instant.now())) {finish(row,"EXPIRED",null);return null;}
            if(!transport.enabled(row.channel)) {row.failure="Channel disabled";row.nextAttemptAt=Instant.now().plusSeconds(60);return null;}
            row.status="SENDING";row.nextAttemptAt=Instant.now().plusSeconds(120);row.attempts++;return row;
        });
        if(d==null) return;
        String result="ACCEPTED",failure=null,providerId=null;
        boolean prepared=false;
        try {
            if(d.orderId!=null) {
                var contact=recipients.get(d.userId);
                if(contact==null) throw new IllegalStateException("Missing contact response");
                if(!contact.permits(d.channel)) {complete(id,"SKIPPED","Customer preference or inactive account",null);return;}
                d.recipient=d.channel==Delivery.Channel.EMAIL?contact.email():contact.phone();
            }
            if(!d.expiresAt.isAfter(Instant.now())) {complete(id,"EXPIRED",null,null);return;}
            prepared=true;providerId=transport.send(d);
        } catch(MessageTransport.Rejected ex) {result=ex.retryable?"PENDING":"FAILED";failure="Provider rejected delivery";}
        catch(RuntimeException ex) {result=prepared?"UNKNOWN":"PENDING";failure=prepared?"Provider outcome uncertain; inspect provider before resending":"Recipient lookup unavailable";}
        complete(id,result,failure,providerId);
    }
    private void complete(String id,String status,String failure,String providerId) {
        tx.executeWithoutResult(s -> {Delivery row=deliveries.lock(id).orElseThrow();if(!row.status.equals("SENDING")) return;
            row.providerId=providerId;
            if(status.equals("PENDING") && row.attempts<5) {row.status=status;row.failure=failure;row.nextAttemptAt=Instant.now().plusSeconds(30L*(1L<<Math.min(row.attempts,6)));}
            else finish(row,status.equals("PENDING")?"FAILED":status,failure);
        });
    }
    private static void finish(Delivery row,String status,String failure) {row.status=status;row.failure=failure;row.body="";row.recipient=null;}
}
