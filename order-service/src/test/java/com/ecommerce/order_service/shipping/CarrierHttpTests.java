package com.ecommerce.order_service.shipping;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import feign.*;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.*;
import static org.assertj.core.api.Assertions.*;

class CarrierHttpTests {
    @Test void delhiveryUsesOneFormEncodedPostAndTokenHeader() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var body=new AtomicReference<String>();var auth=new AtomicReference<String>();var contentType=new AtomicReference<String>();
        server.createContext("/api/cmu/create.json",exchange->{
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));auth.set(exchange.getRequestHeaders().getFirst("Authorization"));contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] response="{\"success\":true}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        try {
            var client=Feign.builder().contract(new SpringMvcContract()).encoder(new SpringEncoder(()->new HttpMessageConverters()))
                .decoder(new SpringDecoder(()->new HttpMessageConverters())).retryer(Retryer.NEVER_RETRY)
                .target(DelhiveryApi.class,"http://127.0.0.1:"+server.getAddress().getPort());
            String form="format=json&data="+URLEncoder.encode("{\"address\":\"A&B\"}",StandardCharsets.UTF_8);
            assertThat(client.create("Token fake-test-token",form).path("success").asBoolean()).isTrue();
            assertThat(body.get()).isEqualTo(form);assertThat(auth.get()).isEqualTo("Token fake-test-token");assertThat(contentType.get()).startsWith("application/x-www-form-urlencoded");
        } finally {server.stop(0);}
    }
}
