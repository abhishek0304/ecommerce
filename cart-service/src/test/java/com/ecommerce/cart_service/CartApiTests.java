package com.ecommerce.cart_service;

import com.ecommerce.cart_service.entity.Cart;
import com.ecommerce.cart_service.repository.CartRepository;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManagerFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CartApiTests {
    static final String SECRET = "change-this-development-secret-key-to-at-least-32-bytes";
    static final Map<String, Reply> catalog = new ConcurrentHashMap<>();
    static final HttpServer productServer = startServer();
    record Reply(int status, String body) {}
    static HttpServer startServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                Reply reply = "GET".equals(exchange.getRequestMethod())
                        ? catalog.getOrDefault(exchange.getRequestURI().getPath(), new Reply(404, "{}"))
                        : new Reply(405, "{}");
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start();
            return server;
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("product-service.url", () -> "http://127.0.0.1:" + productServer.getAddress().getPort());
    }
    @AfterAll static void stopServer() { productServer.stop(0); }
    @Autowired MockMvc mvc;
    @Autowired CartRepository carts;
    @Autowired EntityManagerFactory entityManagers;

    @BeforeEach void setup() {
        carts.deleteAll();
        catalog.clear();
        product(1, "12.50", 10, true);
        product(2, "3.25", 10, true);
    }
    void product(long id, String price, int stock, boolean active) {
        catalog.put("/api/v1/products/" + id, new Reply(200,
                "{\"id\":" + id + ",\"name\":\"Product " + id + "\",\"price\":" + price
                + ",\"stockQuantity\":" + stock + ",\"active\":" + active + ",\"sku\":\"SKU-" + id + "\"}"));
    }
    String token(long id) { return token(id, SECRET, Instant.now().plusSeconds(600)); }
    String token(long id, String secret, Instant expires) {
        return "Bearer " + Jwts.builder().subject("user" + id + "@example.com").claim("userId", id)
                .claim("roles", java.util.List.of("ROLE_USER")).expiration(Date.from(expires))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
    void addItem(long user, long product, int quantity) throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", token(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":" + quantity + "}")).andExpect(status().isOk());
    }

    @Test void fullLifecyclePersistsAndUsesCurrentPrices() throws Exception {
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalPrice").value(0));
        addItem(1, 1, 2);
        addItem(1, 1, 1);
        addItem(1, 2, 2);
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.totalQuantity").value(5)).andExpect(jsonPath("$.totalPrice").value(44));
        mvc.perform(put("/api/cart/items/1").header("Authorization", token(1)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":4}")).andExpect(status().isOk()).andExpect(jsonPath("$.totalPrice").value(56.5));
        product(1, "15.00", 10, true);
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(jsonPath("$.totalPrice").value(66.5));
        mvc.perform(delete("/api/cart/items/1").header("Authorization", token(1))).andExpect(status().isNoContent());
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(jsonPath("$.totalQuantity").value(2));
        mvc.perform(delete("/api/cart").header("Authorization", token(1))).andExpect(status().isNoContent());
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void eachUserCanOnlyAccessTheirOwnCart() throws Exception {
        addItem(1, 1, 2);
        mvc.perform(get("/api/cart?userId=1").header("Authorization", token(2))).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(put("/api/cart/items/1").header("Authorization", token(2)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":4}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/cart/items/1").header("Authorization", token(2))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/cart").header("Authorization", token(2))).andExpect(status().isNoContent());
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(jsonPath("$.totalQuantity").value(2));
    }
    @Test void cartVersionDistinguishesRepeatedPurchasesWithIdenticalItems() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        addItem(1, 1, 1);
        String first = mvc.perform(get("/api/cart").header("Authorization", token(1))).andReturn().getResponse().getContentAsString();
        long before = json.readTree(first).path("version").asLong(-1);
        assertThat(before).isGreaterThanOrEqualTo(0);
        mvc.perform(delete("/api/cart").header("Authorization", token(1))).andExpect(status().isNoContent());
        addItem(1, 1, 1);
        String second = mvc.perform(get("/api/cart").header("Authorization", token(1))).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(second).path("version").asLong()).isGreaterThan(before);
        assertThat(json.readTree(second).path("items")).isEqualTo(json.readTree(first).path("items"));
    }

    @Test void allEndpointsRequireAuthentication() throws Exception {
        for (var request : java.util.List.of(get("/api/cart"), post("/api/cart/items"),
                put("/api/cart/items/1"), delete("/api/cart/items/1"), delete("/api/cart"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
    }

    @Test void rejectsInvalidExpiredAndWronglySignedTokens() throws Exception {
        for (String invalid : java.util.List.of("Bearer invalid", token(1, SECRET, Instant.now().minusSeconds(60)),
                token(1, "a-different-secret-with-at-least-thirty-two-bytes", Instant.now().plusSeconds(60)),
                token(0))) {
            mvc.perform(get("/api/cart").header("Authorization", invalid)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(status().isOk());
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    }

    @Test void rejectsInvalidBodiesAndPaths() throws Exception {
        for (String body : java.util.List.of("{}", "{\"productId\":1}", "{\"productId\":0,\"quantity\":1}",
                "{\"productId\":1,\"quantity\":0}", "{\"productId\":1,\"quantity\":-1}", "{")) {
            mvc.perform(post("/api/cart/items").header("Authorization", token(1)).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/cart/items/1").header("Authorization", token(1)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":0}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/cart/items/-1").header("Authorization", token(1))).andExpect(status().isBadRequest());
        assertThat(carts.count()).isZero();
    }

    @Test void rejectsMissingInactiveAndInsufficientStockWithoutChangingCart() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", token(1)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":999,\"quantity\":1}")).andExpect(status().isNotFound());
        product(1, "12.50", 10, false);
        mvc.perform(post("/api/cart/items").header("Authorization", token(1)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"quantity\":1}")).andExpect(status().isConflict());
        product(1, "12.50", 10, true);
        addItem(1, 1, 8);
        mvc.perform(post("/api/cart/items").header("Authorization", token(1)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"quantity\":3}")).andExpect(status().isConflict());
        mvc.perform(put("/api/cart/items/1").header("Authorization", token(1)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":11}")).andExpect(status().isConflict());
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(jsonPath("$.totalQuantity").value(8));
    }

    @Test void productFailureReturns503AndRollsBackMutation() throws Exception {
        addItem(1, 1, 2);
        catalog.put("/api/v1/products/1", new Reply(500, "{}"));
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(status().isServiceUnavailable());
        // Product 2 is valid, but reading the other cart item fails after the mutation.
        mvc.perform(post("/api/cart/items").header("Authorization", token(1)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":2,\"quantity\":1}")).andExpect(status().isServiceUnavailable());
        product(1, "12.50", 10, true);
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalQuantity").value(2));
    }

    @Test void deletedProductsRemainRemovableAndDeletesAreIdempotent() throws Exception {
        addItem(1, 1, 2);
        catalog.clear();
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].available").value(false)).andExpect(jsonPath("$.totalPrice").value(0));
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/cart/items/1").header("Authorization", token(1))).andExpect(status().isNoContent());
            mvc.perform(delete("/api/cart").header("Authorization", token(1))).andExpect(status().isNoContent());
        }
    }

    @Test void malformedProductResponseDoesNotCreateCart() throws Exception {
        catalog.put("/api/v1/products/1", new Reply(200, "{\"id\":1}"));
        mvc.perform(post("/api/cart/items").header("Authorization", token(1)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"quantity\":1}")).andExpect(status().isServiceUnavailable());
        assertThat(carts.count()).isZero();
    }

    @Test void simultaneousEditsCannotSilentlyOverwriteEachOther() throws Exception {
        addItem(1, 1, 1);
        try (var first = entityManagers.createEntityManager(); var second = entityManagers.createEntityManager()) {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Cart a = first.find(Cart.class, 1L);
            Cart b = second.find(Cart.class, 1L);
            a.getItems().size();
            b.getItems().size();
            a.getItems().put(1L, 2);
            b.getItems().put(1L, 3);
            first.getTransaction().commit();
            assertThatThrownBy(() -> second.getTransaction().commit()).isInstanceOf(jakarta.persistence.RollbackException.class);
        }
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(jsonPath("$.totalQuantity").value(2));
    }
    @Test void checkoutSnapshotRequiresInternalKeyAndDoesNotClearNewCartEdits() throws Exception {
        addItem(1, 1, 2);
        mvc.perform(get("/internal/carts/1").header("Authorization", token(1))).andExpect(status().isUnauthorized());
        String key = "local-development-service-key-change-me";
        String snapshot = mvc.perform(get("/internal/carts/1").header("X-Service-Key", key))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long version = new com.fasterxml.jackson.databind.ObjectMapper().readTree(snapshot).path("version").asLong();
        addItem(1, 1, 1);
        mvc.perform(post("/internal/carts/1/consume").header("X-Service-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + "}")).andExpect(status().isOk()).andExpect(jsonPath("$.cleared").value(false));
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(jsonPath("$.totalQuantity").value(3));
        String latest = mvc.perform(get("/internal/carts/1").header("X-Service-Key", key)).andReturn().getResponse().getContentAsString();
        long current = new com.fasterxml.jackson.databind.ObjectMapper().readTree(latest).path("version").asLong();
        mvc.perform(post("/internal/carts/1/consume").header("X-Service-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + current + "}")).andExpect(jsonPath("$.cleared").value(true));
        addItem(1, 1, 1);
        mvc.perform(post("/internal/carts/1/consume").header("X-Service-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + current + "}")).andExpect(jsonPath("$.cleared").value(false));
        mvc.perform(get("/api/cart").header("Authorization", token(1))).andExpect(jsonPath("$.totalQuantity").value(1));
    }}
