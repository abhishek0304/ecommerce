package com.ecommerce.notification_service;
import com.ecommerce.notification_service.delivery.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import feign.Feign;
import feign.Retryer;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProviderTransportTests {
    @Test void smtpUsesConfiguredSenderAndPlainTextContent() {
        var sender=mock(JavaMailSender.class);ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(sender);
        var transport=new ProviderTransport(new MockEnvironment().withProperty("messages.email.from","store@example.com"),provider,mock(TwilioApi.class),new ObjectMapper());
        Delivery d=new Delivery();d.channel=Delivery.Channel.EMAIL;d.recipient="customer@example.com";d.subject="Verify";d.body="Code 123456";
        assertThat(transport.send(d)).isEqualTo("SMTP_ACCEPTED");
        verify(sender).send(argThat((SimpleMailMessage m) -> m.getFrom().equals("store@example.com") && m.getText().equals("Code 123456") && m.getTo()[0].equals(d.recipient)));
    }
    @Test void twilioUsesFormEncodingAndApprovedWhatsAppTemplateAndClassifiesThrottle() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var body=new AtomicReference<String>();var authorization=new AtomicReference<String>();var status=new AtomicInteger(201);
        server.createContext("/2010-04-01/Accounts/AC"+"a".repeat(32)+"/Messages.json",exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response=("{\"sid\":\"SM"+"b".repeat(32)+"\"}").getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status.get(),response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        try {
            var env=new MockEnvironment().withProperty("messages.twilio.base-url","http://127.0.0.1:"+server.getAddress().getPort())
                .withProperty("messages.twilio.account-sid","AC"+"a".repeat(32)).withProperty("messages.twilio.auth-token","fake-token")
                .withProperty("messages.sms.from","+15005550006").withProperty("messages.whatsapp.from","+15005550006").withProperty("messages.whatsapp.content-sid","HX"+"c".repeat(32));
            var api=Feign.builder().contract(new SpringMvcContract())
                .encoder(new SpringEncoder(() -> new HttpMessageConverters()))
                .decoder(new SpringDecoder(() -> new HttpMessageConverters()))
                .retryer(Retryer.NEVER_RETRY)
                .target(TwilioApi.class,env.getProperty("messages.twilio.base-url"));
            var transport=new ProviderTransport(env,mock(ObjectProvider.class),api,new ObjectMapper());
            Delivery d=new Delivery();d.channel=Delivery.Channel.SMS;d.recipient="+919876543210";d.body="Order confirmed";
            transport.send(d);assertThat(body.get()).contains("To=%2B919876543210","Body=Order+confirmed");assertThat(authorization.get()).startsWith("Basic ");
            d.channel=Delivery.Channel.WHATSAPP;transport.send(d);assertThat(body.get()).contains("To=whatsapp%3A%2B919876543210","ContentSid=HX","ContentVariables=").doesNotContain("Body=");
            status.set(429);assertThatThrownBy(() -> transport.send(d)).isInstanceOfSatisfying(MessageTransport.Rejected.class,e -> assertThat(e.retryable).isTrue());
        } finally {server.stop(0);}
    }
}
