package com.ecommerce.api_gateway;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderRouteTests {
    static final HttpServer downstream = startServer();
    static HttpServer startServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String body = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                        + exchange.getRequestHeaders().getFirst("Authorization") + " "
                        + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (var out = exchange.getResponseBody()) { out.write(bytes); }
            });
            server.start();
            return server;
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("order-service.uri", () -> "http://127.0.0.1:" + downstream.getAddress().getPort());
    }
    @AfterAll static void stop() { downstream.stop(0); }
    @LocalServerPort int port;
    @Test void forwardsOrderEndpointsWithAuthenticationAndBody() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (String operation : java.util.List.of("GET /api/orders", "POST /api/orders",
                    "GET /api/orders/1", "POST /api/orders/1/cancel", "POST /api/orders/1/payments/verify", "PATCH /api/admin/orders/1/status", "POST /api/admin/orders/1/refund/retry", "POST /api/payments/razorpay/webhook",
                    "GET /api/admin/orders", "GET /api/admin/orders/1", "GET /api/reviews/products/1", "POST /api/orders/1/reviews",
                    "GET /api/wishlist", "PUT /api/wishlist/1", "DELETE /api/wishlist/1", "POST /api/coupons/quote",
                    "GET /api/admin/coupons", "POST /api/admin/coupons", "PATCH /api/admin/coupons/SAVE10",
                    "POST /api/orders/1/returns", "PATCH /api/admin/orders/1/return", "POST /api/admin/orders/1/return/receive", "POST /api/admin/orders/1/refund/manual")) {
                String[] parts = operation.split(" ");
                String body = parts[0].equals("POST") || parts[0].equals("PUT") ? "{\"quantity\":2}" : "";
                var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + parts[1]))
                        .header("Authorization", "Bearer test-token").header("Content-Type", "application/json")
                        .method(parts[0], HttpRequest.BodyPublishers.ofString(body)).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEqualTo(operation + " Bearer test-token " + body);
            }
        }
    }
    @Test void storefrontAssetsAreServedByGateway() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (String path : java.util.List.of("/index.html", "/store.js", "/store.css")) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isNotBlank();
            }
        }
    }
}
