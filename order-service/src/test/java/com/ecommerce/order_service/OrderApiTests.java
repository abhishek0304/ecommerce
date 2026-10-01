package com.ecommerce.order_service;

import com.ecommerce.order_service.model.OrderRepository;
import com.ecommerce.order_service.service.OrderService;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTests {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Map<String, JsonNode> reservations = new ConcurrentHashMap<>(), providerOrders = new ConcurrentHashMap<>();
    static final AtomicInteger reserveEffects = new AtomicInteger(), releaseEffects = new AtomicInteger(), providerCreates = new AtomicInteger();
    static final Map<String, JsonNode> refunds = new ConcurrentHashMap<>();
    static final AtomicInteger refundEffects = new AtomicInteger(), commitEffects = new AtomicInteger();
    static volatile boolean lostRefundResponse, releaseUnavailable, providerUnavailable;
    static volatile String refundState = "pending";
    static final Set<String> released = ConcurrentHashMap.newKeySet();
    static volatile boolean stockFailure, lostInventoryResponse, lostProviderResponse, captured, wrongAmount, emptyCart;
    static volatile long cartVersion = 1;
    static final HttpServer server = start();
    @Autowired MockMvc mvc;
    @Autowired OrderRepository orders;
    @Autowired com.ecommerce.order_service.commerce.DeliveryRepository deliveryRules;
    @Autowired OrderService service;
    @Autowired com.ecommerce.order_service.events.OutboxRepository outbox;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    static HttpServer start() {
        try {
            var result = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            result.setExecutor(Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; }));
            result.createContext("/", OrderApiTests::handle);
            result.start(); return result;
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    static void handle(HttpExchange exchange) throws java.io.IOException {
        try {
            String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
            if (path.startsWith("/internal/") && !"test-service-key".equals(exchange.getRequestHeaders().getFirst("X-Service-Key"))) {
                reply(exchange, 401, Map.of()); return;
            }
            if (path.equals("/api/addresses")) {
                if (exchange.getRequestHeaders().getFirst("Authorization") == null) { reply(exchange, 401, Map.of()); return; }
                reply(exchange, 200, List.of(Map.of("id", 1, "line1", "10 Test Road", "city", "Pune", "state", "Maharashtra", "postalCode", "411001", "country", "India"))); return;
            }
            if (path.startsWith("/internal/carts/")) {
                if (path.endsWith("/consume")) { reply(exchange, 200, Map.of("cleared", true)); return; }
                if (emptyCart) { reply(exchange, 409, Map.of()); return; }
                long user = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
                reply(exchange, 200, Map.of("userId", user, "version", cartVersion, "items", Map.of("1", 2))); return;
            }
            if (path.startsWith("/internal/inventory/reservations/")) {
                if (path.endsWith("/commit")) { commitEffects.incrementAndGet(); reply(exchange, 200, Map.of()); return; }
                String id = path.substring(path.lastIndexOf('/') + 1);
                if (method.equals("DELETE")) {
                    if (releaseUnavailable) { reply(exchange, 503, Map.of()); return; }
                    if (released.add(id)) releaseEffects.incrementAndGet();
                    reply(exchange, 200, Map.of()); return;
                }
                if (stockFailure) { reply(exchange, 422, Map.of()); return; }
                JsonNode value = reservations.computeIfAbsent(id, key -> {
                    reserveEffects.incrementAndGet();
                    return JSON.valueToTree(Map.of("id", id, "state", "RESERVED",
                            "items", List.of(Map.of("productId", 1, "name", "Test product", "price", 10.50, "quantity", 2))));
                });
                if (lostInventoryResponse) { lostInventoryResponse = false; reply(exchange, 503, Map.of()); return; }
                reply(exchange, 200, value); return;
            }
            if (!("Basic " + Base64.getEncoder().encodeToString("rzp_test_local:test-provider-secret".getBytes(StandardCharsets.UTF_8))).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                reply(exchange, 401, Map.of()); return;
            }
            if (providerUnavailable) { reply(exchange, 503, Map.of()); return; }
            if (path.endsWith("/refund") && method.equals("POST")) {
                String attempt = exchange.getRequestHeaders().getFirst("X-Refund-Idempotency");
                if (attempt == null || attempt.length() < 10) { reply(exchange, 400, Map.of()); return; }
                JsonNode body = JSON.readTree(exchange.getRequestBody());
                JsonNode refund = refunds.computeIfAbsent(attempt, key -> JSON.valueToTree(Map.of(
                        "id", "rfnd_test" + refundEffects.incrementAndGet(), "payment_id", path.split("/")[3],
                        "amount", body.path("amount").asLong(), "currency", "INR", "status", refundState, "receipt", attempt)));
                if (lostRefundResponse) { lostRefundResponse = false; reply(exchange, 503, Map.of()); return; }
                reply(exchange, 200, refund); return;
            }
            if (path.startsWith("/v1/refunds/")) {
                String refundId = path.substring(path.lastIndexOf('/') + 1);
                JsonNode found = refunds.values().stream().filter(r -> r.path("id").asText().equals(refundId)).findFirst().orElse(null);
                if (found == null) { reply(exchange, 404, Map.of()); return; }
                var updated = found.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) updated).put("status", refundState);
                reply(exchange, 200, updated); return;
            }
            if (path.equals("/v1/orders") && method.equals("POST")) {
                JsonNode body = JSON.readTree(exchange.getRequestBody());
                int n = providerCreates.incrementAndGet();
                JsonNode order = JSON.valueToTree(Map.of("id", "order_test" + n, "receipt", body.path("receipt").asText(),
                        "amount", body.path("amount").asLong(), "currency", "INR", "status", "created"));
                providerOrders.put(body.path("receipt").asText(), order);
                if (lostProviderResponse) { lostProviderResponse = false; reply(exchange, 503, Map.of()); return; }
                reply(exchange, 200, order); return;
            }
            if (path.equals("/v1/orders")) {
                String query = exchange.getRequestURI().getQuery();
                String receipt = Arrays.stream(query.split("&")).filter(p -> p.startsWith("receipt=")).findFirst().orElse("receipt=").substring(8);
                JsonNode order = providerOrders.get(receipt);
                reply(exchange, 200, Map.of("items", order == null ? List.of() : List.of(order))); return;
            }
            if (path.startsWith("/v1/payments/")) {
                reply(exchange, 200, payment(path.substring(path.lastIndexOf('/') + 1), "order_test1")); return;
            }
            if (path.endsWith("/payments")) {
                String id = path.split("/")[3];
                reply(exchange, 200, Map.of("items", List.of(payment("pay_test1", id)))); return;
            }
            if (path.startsWith("/v1/orders/")) {
                reply(exchange, 200, Map.of("id", path.substring(path.lastIndexOf('/') + 1), "status", captured ? "paid" : "created")); return;
            }
            reply(exchange, 404, Map.of());
        } catch (Exception ex) { reply(exchange, 500, Map.of("error", ex.getClass().getSimpleName())); }
    }
    static Map<String, Object> payment(String id, String orderId) {
        return Map.of("id", id, "order_id", orderId, "status", captured ? "captured" : "authorized",
                "amount", wrongAmount ? 100 : 2100, "currency", "INR", "amount_refunded", 0);
    }
    static void reply(HttpExchange e, int status, Object body) throws java.io.IOException {
        byte[] data = JSON.writeValueAsBytes(body);
        e.getResponseHeaders().set("Content-Type", "application/json");
        e.sendResponseHeaders(status, data.length);
        try (var out = e.getResponseBody()) { out.write(data); }
    }
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        r.add("cart-service.url", () -> base); r.add("product-service.url", () -> base);
        r.add("user-service.url", () -> base); r.add("razorpay.base-url", () -> base + "/v1");
    }
    @AfterAll static void stop() { server.stop(0); }
    @BeforeEach void reset() {
        outbox.deleteAll(); orders.deleteAll(); refunds.clear(); refundEffects.set(0); commitEffects.set(0);
        lostRefundResponse = releaseUnavailable = providerUnavailable = false; refundState = "pending"; reservations.clear(); providerOrders.clear(); released.clear();
        reserveEffects.set(0); releaseEffects.set(0); providerCreates.set(0);
        stockFailure = lostInventoryResponse = lostProviderResponse = captured = wrongAmount = emptyCart = false; cartVersion = 1;
    }
    String token(long user) {
        return "Bearer " + Jwts.builder().subject("user" + user + "@example.com").claim("userId", user)
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("change-this-development-secret-key-to-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
    ResultActions checkout(String key, String payment, long user) throws Exception {
        return mvc.perform(post("/api/orders").header("Authorization", token(user)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":1,\"paymentMethod\":\"" + payment + "\"}"));
    }
    JsonNode body(ResultActions result) throws Exception { return JSON.readTree(result.andReturn().getResponse().getContentAsString()); }
    String signature(String secret, String message) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }
    ResultActions verify(String id, String signed) throws Exception {
        return mvc.perform(post("/api/orders/" + id + "/payments/verify").header("Authorization", token(1))
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
                        "razorpay_order_id", "order_test1", "razorpay_payment_id", "pay_test1", "razorpay_signature", signed))));
    }

    @Test void codRetriesReturnSameOrderEvenAfterCartIsEmpty() throws Exception {
        JsonNode first = body(checkout("checkout-001", "CASH_ON_DELIVERY", 1).andExpect(status().isOk()));
        assertThat(first.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(first.path("paymentStatus").asText()).isEqualTo("UNPAID");
        assertThat(first.path("totalPrice").decimalValue()).isEqualByComparingTo("21.00");
        emptyCart = true;
        JsonNode retry = body(checkout("checkout-001", "CASH_ON_DELIVERY", 1).andExpect(status().isOk()));
        assertThat(retry.path("id")).isEqualTo(first.path("id"));
        assertThat(orders.count()).isEqualTo(1); assertThat(reserveEffects.get()).isEqualTo(1);
        assertThat(providerCreates.get()).isZero();
    }

    @Test void deliveryFeesAreSnapshottedAndAttemptLookupIsOwned() throws Exception {
        var rule=new com.ecommerce.order_service.commerce.DeliveryRule();rule.postalPrefix="411";rule.fee=new java.math.BigDecimal("5");rule.freeAbove=new java.math.BigDecimal("100");rule.minDays=2;rule.maxDays=4;deliveryRules.saveAndFlush(rule);
        try {
            var first=body(checkout("delivery-001","CASH_ON_DELIVERY",1).andExpect(status().isOk()));
            assertThat(first.path("totalPrice").decimalValue()).isEqualByComparingTo("26");assertThat(first.path("subtotal").decimalValue()).isEqualByComparingTo("21");assertThat(first.path("deliveryFee").decimalValue()).isEqualByComparingTo("5");
            rule.fee=new java.math.BigDecimal("50");deliveryRules.saveAndFlush(rule);
            checkout("delivery-001","CASH_ON_DELIVERY",1).andExpect(jsonPath("$.totalPrice").value(26));
            mvc.perform(get("/api/orders/attempts/delivery-001").header("Authorization",token(1))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.path("id").asText()));
            mvc.perform(get("/api/orders/attempts/delivery-001").header("Authorization",token(2))).andExpect(status().isNotFound());
        } finally {deliveryRules.deleteAll();}
    }
    @Test void concurrentIdenticalRequestsCreateOneOrder() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<String> request = () -> { start.await(); return body(checkout("concurrent-001", "CASH_ON_DELIVERY", 1).andExpect(status().isOk())).path("id").asText(); };
            var a = pool.submit(request); var b = pool.submit(request); start.countDown();
            assertThat(a.get(30, TimeUnit.SECONDS)).isEqualTo(b.get(30, TimeUnit.SECONDS));
        }
        assertThat(orders.count()).isEqualTo(1); assertThat(reserveEffects.get()).isEqualTo(1);
    }
    @Test void changedPayloadOrNewKeyForSameCartCannotDuplicateOrder() throws Exception {
        checkout("checkout-001", "CASH_ON_DELIVERY", 1).andExpect(status().isOk());
        checkout("checkout-001", "RAZORPAY", 1).andExpect(status().isConflict());
        checkout("checkout-002", "CASH_ON_DELIVERY", 1).andExpect(status().isConflict());
        assertThat(orders.count()).isEqualTo(1);
    }
    @Test void usersCannotReadCancelOrVerifyOtherUsersOrders() throws Exception {
        String id = body(checkout("checkout-001", "CASH_ON_DELIVERY", 1)).path("id").asText();
        mvc.perform(get("/api/orders/" + id).header("Authorization", token(2))).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", token(2))).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders").header("Authorization", token(2))).andExpect(jsonPath("$.totalElements").value(0));
        checkout("checkout-001", "CASH_ON_DELIVERY", 2).andExpect(status().isOk());
        assertThat(orders.count()).isEqualTo(2);
    }
    @Test void stockRejectionIsDurableAndDoesNotCreateAnotherOrder() throws Exception {
        stockFailure = true;
        String first = body(checkout("checkout-001", "CASH_ON_DELIVERY", 1).andExpect(status().isConflict())).path("id").asText();
        stockFailure = false;
        assertThat(body(checkout("checkout-001", "CASH_ON_DELIVERY", 1).andExpect(status().isConflict())).path("id").asText()).isEqualTo(first);
        assertThat(reserveEffects.get()).isZero();
    }
    @Test void lostInventoryResponseCanBeRecoveredWithoutDeductingStockAgain() throws Exception {
        lostInventoryResponse = true;
        String id = body(checkout("checkout-001", "CASH_ON_DELIVERY", 1).andExpect(status().isAccepted())).path("id").asText();
        service.advance(id);
        mvc.perform(get("/api/orders/" + id).header("Authorization", token(1))).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(reserveEffects.get()).isEqualTo(1);
    }
    @Test void cancellationReleasesStockOnlyOnce() throws Exception {
        String id = body(checkout("checkout-001", "CASH_ON_DELIVERY", 1)).path("id").asText();
        for (int i = 0; i < 2; i++)
            mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", token(1)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(releaseEffects.get()).isEqualTo(1);
    }
    @Test void razorpayRetryReusesPaymentOrderAndRequiresCapturedMatchingPayment() throws Exception {
        String id = body(checkout("checkout-001", "RAZORPAY", 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))).path("id").asText();
        checkout("checkout-001", "RAZORPAY", 1).andExpect(status().isOk());
        assertThat(providerCreates.get()).isEqualTo(1);
        verify(id, "0".repeat(64)).andExpect(status().isBadRequest());
        String signed = signature("test-provider-secret", "order_test1|pay_test1");
        verify(id, signed).andExpect(status().isConflict());
        captured = true; wrongAmount = true;
        verify(id, signed).andExpect(status().isConflict());
        wrongAmount = false;
        verify(id, signed).andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        verify(id, signed).andExpect(status().isOk());
        assertThat(reserveEffects.get()).isEqualTo(1);
    }
    @Test void uncertainRazorpayCreationIsReconciledWithoutSecondPost() throws Exception {
        lostProviderResponse = true;
        String id = body(checkout("checkout-001", "RAZORPAY", 1).andExpect(status().isAccepted())).path("id").asText();
        service.advance(id);
        mvc.perform(get("/api/orders/" + id).header("Authorization", token(1)))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertThat(providerCreates.get()).isEqualTo(1);
    }
    @Test void signedWebhookConfirmsAndDuplicateEventsAreHarmless() throws Exception {
        String id = body(checkout("checkout-001", "RAZORPAY", 1)).path("id").asText();
        String event = "{\"event\":\"order.paid\",\"payload\":{\"order\":{\"entity\":{\"id\":\"order_test1\"}}}}";
        mvc.perform(post("/api/payments/razorpay/webhook").header("X-Razorpay-Signature", "00").contentType(MediaType.APPLICATION_JSON).content(event))
                .andExpect(status().isBadRequest());
        captured = true;
        for (int i = 0; i < 2; i++)
            mvc.perform(post("/api/payments/razorpay/webhook").header("X-Razorpay-Signature", signature("test-webhook-secret", event))
                    .contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isNoContent());
        mvc.perform(get("/api/orders/" + id).header("Authorization", token(1))).andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }
    @Test void unauthenticatedAndInvalidCheckoutRequestsAreRejected() throws Exception {
        mvc.perform(post("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders").header("Authorization", token(1)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":1,\"paymentMethod\":\"CASH_ON_DELIVERY\"}")).andExpect(status().isBadRequest());
        checkout("short", "CASH_ON_DELIVERY", 1).andExpect(status().isBadRequest());
        checkout("checkout-001", "INVALID", 1).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").header("Authorization", token(1)).header("Idempotency-Key", "checkout-001")
                .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":2,\"paymentMethod\":\"CASH_ON_DELIVERY\"}")).andExpect(status().isNotFound());
        assertThat(orders.count()).isZero();
    }

    String adminToken() {
        return "Bearer " + Jwts.builder().subject("admin@example.com").claim("userId", 99L)
                .claim("roles", List.of("ROLE_ADMIN")).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("change-this-development-secret-key-to-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
    void expire(String id) {
        var order = orders.findById(id).orElseThrow(); order.expiresAt = Instant.now().minusSeconds(1); orders.saveAndFlush(order);
    }
    ResultActions changeStatus(String id, String body, String token) throws Exception {
        return mvc.perform(patch("/api/admin/orders/" + id + "/status").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    @Test void expirationReleasesStockEvenWhenPaymentProviderIsUnavailable() throws Exception {
        String id = body(checkout("checkout-expire", "RAZORPAY", 1)).path("id").asText();
        expire(id); providerUnavailable = true;
        service.advance(id); service.advance(id);
        assertThat(orders.findById(id).orElseThrow().status).isEqualTo("EXPIRED");
        assertThat(releaseEffects.get()).isEqualTo(1);
    }
    @Test void expiredPaymentIsRefundedInsteadOfBeingFulfilled() throws Exception {
        String id = body(checkout("checkout-expire", "RAZORPAY", 1)).path("id").asText();
        expire(id); service.advance(id); captured = true;
        service.advance(id);
        var order = orders.findById(id).orElseThrow();
        assertThat(order.status).isEqualTo("EXPIRED"); assertThat(order.refundStatus).isEqualTo("PENDING");
        assertThat(refundEffects.get()).isEqualTo(1); assertThat(releaseEffects.get()).isEqualTo(1);
        refundState = "processed"; service.advance(id); service.advance(id);
        assertThat(orders.findById(id).orElseThrow().paymentStatus).isEqualTo("REFUNDED");
        assertThat(refundEffects.get()).isEqualTo(1);
        changeStatus(id, "{\"status\":\"PROCESSING\"}", adminToken()).andExpect(status().isConflict());
    }
    @Test void onlineCancellationRefundsCapturedPaymentsAndRecoversLostRefundResponses() throws Exception {
        String id = body(checkout("checkout-refund", "RAZORPAY", 1)).path("id").asText();
        captured = true; verify(id, signature("test-provider-secret", "order_test1|pay_test1")).andExpect(status().isOk());
        lostRefundResponse = true;
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", token(1))).andExpect(status().isOk());
        String attempt = orders.findById(id).orElseThrow().refundAttemptId;
        service.advance(id);
        assertThat(orders.findById(id).orElseThrow().refundAttemptId).isEqualTo(attempt);
        assertThat(refundEffects.get()).isEqualTo(1);
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", token(1))).andExpect(status().isOk());
        assertThat(refundEffects.get()).isEqualTo(1);
    }
    @Test void paymentAndCancellationRaceNeverReopensCancelledOrder() throws Exception {
        String id = body(checkout("checkout-race", "RAZORPAY", 1)).path("id").asText(); captured = true;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CountDownLatch(1);
            var cancel = pool.submit(() -> { barrier.await(); return service.cancel(id, 1L); });
            var pay = pool.submit(() -> { barrier.await(); service.advance(id); return true; });
            barrier.countDown(); cancel.get(30, TimeUnit.SECONDS); pay.get(30, TimeUnit.SECONDS);
        }
        service.advance(id);
        assertThat(orders.findById(id).orElseThrow().status).isEqualTo("CANCELLED");
        assertThat(refundEffects.get()).isEqualTo(1); assertThat(releaseEffects.get()).isEqualTo(1);
    }
    @Test void failedRefundCanBeRetriedOnlyByAdminAndWebhooksReconcileProgress() throws Exception {
        String id = body(checkout("checkout-refund", "RAZORPAY", 1)).path("id").asText(); captured = true;
        service.advance(id); refundState = "failed"; service.cancel(id, 1L);
        assertThat(orders.findById(id).orElseThrow().refundStatus).isEqualTo("FAILED");
        mvc.perform(post("/api/admin/orders/" + id + "/refund/retry").header("Authorization", token(1))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/orders/" + id + "/refund/retry").header("Authorization", adminToken())).andExpect(status().isOk());
        assertThat(refundEffects.get()).isEqualTo(2);
        refundState = "processed";
        String event = "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"payment_id\":\"pay_test1\"}}}}";
        for (int i = 0; i < 2; i++)
            mvc.perform(post("/api/payments/razorpay/webhook").header("X-Razorpay-Signature", signature("test-webhook-secret", event))
                    .contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isNoContent());
        assertThat(orders.findById(id).orElseThrow().refundStatus).isEqualTo("PROCESSED");
    }
    @Test void fulfillmentRequiresAdminSequentialStatesAndCashCollection() throws Exception {
        String id = body(checkout("checkout-ship", "CASH_ON_DELIVERY", 1)).path("id").asText();
        changeStatus(id, "{\"status\":\"PROCESSING\"}", token(1)).andExpect(status().isForbidden());
        changeStatus(id, "{\"status\":\"DELIVERED\"}", adminToken()).andExpect(status().isConflict());
        changeStatus(id, "{\"status\":\"PROCESSING\"}", adminToken()).andExpect(status().isOk());
        changeStatus(id, "{\"status\":\"SHIPPED\"}", adminToken()).andExpect(status().isBadRequest());
        String shipping = "{\"status\":\"SHIPPED\",\"carrier\":\"Test Carrier\",\"trackingNumber\":\"TRACK123\"}";
        changeStatus(id, shipping, adminToken()).andExpect(status().isOk());
        long count = outbox.count();
        changeStatus(id, shipping, adminToken()).andExpect(status().isOk()); assertThat(outbox.count()).isEqualTo(count);
        assertThat(commitEffects.get()).isEqualTo(1);
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", token(1))).andExpect(status().isConflict());
        changeStatus(id, "{\"status\":\"DELIVERED\"}", adminToken()).andExpect(status().isBadRequest());
        changeStatus(id, "{\"status\":\"DELIVERED\",\"cashCollected\":true}", adminToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("COLLECTED"));
    }
    @Test void unpaidOnlineOrderCannotEnterFulfillment() throws Exception {
        String id = body(checkout("checkout-unpaid", "RAZORPAY", 1)).path("id").asText();
        changeStatus(id, "{\"status\":\"PROCESSING\"}", adminToken()).andExpect(status().isConflict());
        assertThat(outbox.findAll()).hasSize(1);
    }
    @Test void expiredUnknownProviderCreationIsReconciledWithoutNewPaymentOrder() throws Exception {
        lostProviderResponse = true;
        String id = body(checkout("checkout-unknown", "RAZORPAY", 1)).path("id").asText();
        expire(id); captured = true; service.advance(id);
        assertThat(orders.findById(id).orElseThrow().status).isEqualTo("EXPIRED");
        assertThat(refundEffects.get()).isEqualTo(1); assertThat(providerCreates.get()).isEqualTo(1);
    }
    @Test void cancellationRetriesStockReleaseAfterOutage() throws Exception {
        String id = body(checkout("checkout-release", "CASH_ON_DELIVERY", 1)).path("id").asText();
        releaseUnavailable = true; service.cancel(id, 1L);
        assertThat(orders.findById(id).orElseThrow().status).isEqualTo("CANCELLING");
        releaseUnavailable = false; service.advance(id);
        assertThat(orders.findById(id).orElseThrow().status).isEqualTo("CANCELLED");
        assertThat(releaseEffects.get()).isEqualTo(1);
    }
    @Test @SuppressWarnings("unchecked") void kafkaOutboxRetainsUnacknowledgedEventsAndRetries() throws Exception {
        checkout("checkout-events", "CASH_ON_DELIVERY", 1).andExpect(status().isOk());
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka = org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
        org.mockito.Mockito.when(kafka.send(org.mockito.ArgumentMatchers.eq("order-events"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));
        var tracing = new com.ecommerce.order_service.events.OutboxTracing(
                new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(io.micrometer.tracing.Tracer.class),
                new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(io.micrometer.tracing.propagation.Propagator.class),
                io.micrometer.observation.ObservationRegistry.NOOP);
        var publisher = new com.ecommerce.order_service.events.OutboxPublisher(outbox, kafka, transactions, tracing);
        assertThat(publisher.publishNext()).isFalse(); assertThat(outbox.findAll().getFirst().publishedAt).isNull();
        assertThat(publisher.publishNext()).isTrue(); assertThat(outbox.findAll().getFirst().publishedAt).isNotNull();
        assertThat(publisher.publishNext()).isFalse();
    }
}
