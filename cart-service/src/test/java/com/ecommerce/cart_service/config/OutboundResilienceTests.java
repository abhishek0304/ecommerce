package com.ecommerce.cart_service.config;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.*;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import feign.*;
import static org.junit.jupiter.api.Assertions.*;
class OutboundResilienceTests {
    interface Probe { @RequestLine("GET /") void get(); }
    @Test void opensOnFailureIgnoresBusinessErrorsAndRecovers() throws Exception {
        var calls=new AtomicInteger(); var status=new AtomicInteger(404);
        var server=HttpServer.create(new InetSocketAddress("localhost",0),0);
        server.createContext("/", exchange->{calls.incrementAndGet();exchange.sendResponseHeaders(status.get(),-1);exchange.close();});
        server.start();
        try {
            String authority="localhost:"+server.getAddress().getPort();
            var registry=CircuitBreakerRegistry.ofDefaults();
            var client=Feign.builder().retryer(Retryer.NEVER_RETRY)
                    .addCapability(new OutboundResilienceConfig().outboundCircuitBreakers(registry))
                    .target(Probe.class,"http://"+authority);
            for(int i=0;i<20;i++)assertThrows(FeignException.class,client::get);
            assertEquals(CircuitBreaker.State.CLOSED,registry.circuitBreaker(authority).getState());
            status.set(503);
            for(int i=0;i<10;i++)assertThrows(FeignException.class,client::get);
            int attempts=calls.get(); assertEquals(30, attempts);
            assertThrows(RetryableException.class,client::get);
            assertEquals(attempts,calls.get());
            registry.circuitBreaker(authority).transitionToHalfOpenState();status.set(200);
            for(int i=0;i<3;i++)client.get();
            assertEquals(CircuitBreaker.State.CLOSED,registry.circuitBreaker(authority).getState());
        } finally {server.stop(0);}
    }
}
