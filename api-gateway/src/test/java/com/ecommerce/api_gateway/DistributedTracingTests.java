package com.ecommerce.api_gateway;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.reactor.context-propagation=auto",
    "management.tracing.propagation.type=w3c",
    "management.tracing.sampling.probability=1.0",
    "management.otlp.tracing.export.enabled=false"
})
@AutoConfigureObservability
class DistributedTracingTests {
    static final HttpServer downstream = startServer();
    static HttpServer startServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = String.valueOf(exchange.getRequestHeaders().getFirst("traceparent"))
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(exchange.getRequestURI().getPath().endsWith("/fail") ? 500 : 200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            return server;
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("product-service.uri", () -> "http://127.0.0.1:" + downstream.getAddress().getPort());
    }
    @AfterAll static void stop() { downstream.stop(0); }
    @LocalServerPort int port;

    @Test void generatesTraceAndPropagatesItDownstream() throws Exception {
        var response = request("/api/v1/products/1", null);
        String traceId = response.headers().firstValue("X-Trace-Id").orElseThrow();
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith("00-" + traceId + "-").endsWith("-01");
    }

    @Test void continuesIncomingTraceAndExposesIdOnErrors() throws Exception {
        String traceId = "1234567890abcdef1234567890abcdef";
        String parent = "00-" + traceId + "-1234567890abcdef-01";
        var response = request("/api/v1/products/fail", parent);
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("X-Trace-Id")).contains(traceId);
        assertThat(response.body()).startsWith("00-" + traceId + "-").isNotEqualTo(parent);
    }

    @Test void malformedParentDoesNotPreventRequest() throws Exception {
        var response = request("/api/v1/products/1", "invalid");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Trace-Id").orElseThrow()).matches("[0-9a-f]{32}");
    }

    private HttpResponse<String> request(String path, String parent) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (parent != null) request.header("traceparent", parent);
            return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
