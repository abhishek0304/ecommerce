package com.ecommerce.notification_service;
import com.ecommerce.notification_service.delivery.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class DeliveryTests {
    @Autowired DeliveryQueue queue;@Autowired DeliveryRepository repository;@Autowired DeliveryWorker worker;
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;
    @MockitoBean MessageTransport transport;@MockitoBean RecipientClient contacts;
    DeliveryQueue.Request request() {return new DeliveryQueue.Request(1L,Delivery.Channel.EMAIL,"customer@example.com","Verify email","Code 123456",Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));}
    String enqueue() {String id=UUID.randomUUID().toString();queue.enqueue(id,request());return id;}
    @BeforeEach void setup() {repository.deleteAll();when(transport.enabled(any())).thenReturn(true);when(transport.send(any())).thenReturn("provider-reference");}
    @Test void internalApiRequiresServiceKeyAndDeduplicatesPayload() throws Exception {
        String id=UUID.randomUUID().toString(), body=json.writeValueAsString(request());
        mvc.perform(put("/internal/messages/"+id).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put("/internal/messages/"+id).header("Authorization",new NotificationTests().token(1)).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        for(int i=0;i<2;i++) mvc.perform(put("/internal/messages/"+id).header("X-Service-Key","local-development-service-key-change-me").contentType("application/json").content(body)).andExpect(status().isAccepted());
        mvc.perform(put("/internal/messages/"+id).header("X-Service-Key","local-development-service-key-change-me").contentType("application/json").content(body.replace("123456","654321"))).andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }
    @Test void acceptanceRedactsSensitiveFieldsAndDoesNotResend() throws Exception {
        String id=enqueue();worker.process(id);worker.process(id);
        Delivery d=repository.findById(id).orElseThrow();assertThat(d.status).isEqualTo("ACCEPTED");assertThat(d.body).isEmpty();assertThat(d.recipient).isNull();verify(transport,times(1)).send(any());
        mvc.perform(get("/api/notifications/deliveries/"+id).header("Authorization",new NotificationTests().token(1)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED")).andExpect(jsonPath("$.body").doesNotExist()).andExpect(jsonPath("$.recipient").doesNotExist());
        mvc.perform(get("/api/notifications/deliveries/"+id).header("Authorization",new NotificationTests().token(2))).andExpect(status().isNotFound());
    }
    @Test void disabledChannelWaitsWithoutConsumingAttemptsAndExpiredOtpIsNeverSent() {
        String id=enqueue();when(transport.enabled(any())).thenReturn(false);worker.process(id);
        Delivery d=repository.findById(id).orElseThrow();assertThat(d.status).isEqualTo("PENDING");assertThat(d.attempts).isZero();
        d.expiresAt=Instant.now().minusSeconds(1);d.nextAttemptAt=Instant.now().minusSeconds(1);repository.saveAndFlush(d);worker.process(id);
        assertThat(repository.findById(id).orElseThrow().status).isEqualTo("EXPIRED");verify(transport,never()).send(any());
    }
    @Test void definiteThrottleRetriesButAmbiguousTimeoutDoesNot() {
        String id=enqueue();when(transport.send(any())).thenThrow(new MessageTransport.Rejected(true));worker.process(id);
        Delivery d=repository.findById(id).orElseThrow();assertThat(d.status).isEqualTo("PENDING");assertThat(d.nextAttemptAt).isAfter(Instant.now());
        d.nextAttemptAt=Instant.now().minusSeconds(1);repository.saveAndFlush(d);doThrow(new IllegalStateException("timeout")).when(transport).send(any());worker.process(id);
        assertThat(repository.findById(id).orElseThrow().status).isEqualTo("UNKNOWN");
    }
    @Test void expiredSendingLeaseIsNotBlindlyReplayed() {
        String id=enqueue();Delivery d=repository.findById(id).orElseThrow();d.status="SENDING";d.nextAttemptAt=Instant.now().minusSeconds(1);repository.saveAndFlush(d);
        worker.process(id);assertThat(repository.findById(id).orElseThrow().status).isEqualTo("UNKNOWN");verify(transport,never()).send(any());
    }
    @Test void orderFanoutIsIdempotentAndHonorsChannelOptOut() {
        String event=UUID.randomUUID().toString(), order=UUID.randomUUID().toString();
        queue.order(event,1L,order,"OrderConfirmed","Confirmed");queue.order(event,1L,order,"OrderConfirmed","Confirmed");assertThat(repository.count()).isEqualTo(3);
        when(contacts.get(1L)).thenReturn(new RecipientClient.Contact(true,"customer@example.com","+919876543210",true,false,false));
        repository.findAll().forEach(d -> worker.process(d.id));
        assertThat(repository.findAll()).filteredOn(d -> d.status.equals("SKIPPED")).hasSize(2);verify(transport,times(1)).send(argThat(d -> d.channel==Delivery.Channel.EMAIL));
    }
}
