package com.ecommerce.cart_service;

import com.ecommerce.cart_service.client.ProductApi;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import feign.Request;
import io.micrometer.tracing.Tracer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
    "management.tracing.sampling.probability=1.0",
    "management.tracing.propagation.type=w3c",
    "management.otlp.tracing.export.enabled=false"
})
@AutoConfigureObservability
class FeignTracingTests {
    static final AtomicReference<String> traceParent = new AtomicReference<>();
    static final AtomicInteger calls = new AtomicInteger();
    static final HttpServer server = start();
    static HttpServer start() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                calls.incrementAndGet();
                traceParent.set(exchange.getRequestHeaders().getFirst("traceparent"));
                byte[] body = "{\"id\":1,\"name\":\"Item\",\"price\":10,\"stockQuantity\":2,\"active\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(exchange.getRequestURI().getPath().endsWith("/2") ? 503 : 200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            return server;
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("product-service.url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterAll static void close() { server.stop(0); }
    @Autowired ProductApi products;
    @Autowired Tracer tracer;
    @Autowired FeignClientFactory clients;

    @Test void propagatesCurrentTraceThroughSpringManagedFeignClient() {
        var parent = tracer.nextSpan().name("cart-request").start();
        try (var scope = tracer.withSpan(parent)) {
            assertThat(products.get(1L).name()).isEqualTo("Item");
            assertThat(traceParent.get()).startsWith("00-" + parent.context().traceId() + "-").endsWith("-01");
            assertThat(traceParent.get()).doesNotContain("-" + parent.context().spanId() + "-");
        } finally { parent.end(); }
    }
    @Test void doesNotAutomaticallyRetryFailure() {
        int before = calls.get();
        assertThatThrownBy(() -> products.get(2L)).isInstanceOf(FeignException.ServiceUnavailable.class);
        assertThat(calls.get() - before).isEqualTo(1);
    }
    @Test void retainsTimeoutsAndDoesNotFollowRedirects() {
        var options = clients.getInstance("product-service", Request.Options.class);
        assertThat(options.connectTimeoutMillis()).isEqualTo(3000);
        assertThat(options.readTimeoutMillis()).isEqualTo(5000);
        assertThat(options.isFollowRedirects()).isFalse();
    }
}
