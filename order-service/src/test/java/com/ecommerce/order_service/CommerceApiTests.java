package com.ecommerce.order_service;

import com.ecommerce.order_service.api.OrderDtos.*;
import com.ecommerce.order_service.client.*;
import com.ecommerce.order_service.commerce.*;
import com.ecommerce.order_service.model.*;
import com.ecommerce.order_service.service.*;
import com.fasterxml.jackson.databind.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class CommerceApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderRepository orders;
    @Autowired OrderService service;
    @Autowired OrderLifecycle lifecycle;
    @Autowired CouponRepository coupons;
    @Autowired CouponService couponService;
    @Autowired ReviewRepository reviews;
    @Autowired WishlistRepository wishlist;
    @Autowired com.ecommerce.order_service.events.OutboxRepository outbox;
    @MockitoBean ServiceClients clients;
    @MockitoBean RazorpayClient razorpay;
    @BeforeEach void setup() {
        reviews.deleteAll(); wishlist.deleteAll(); outbox.deleteAll(); orders.deleteAll(); coupons.deleteAll();
        when(clients.address(eq(1L), anyString())).thenReturn(json.valueToTree(Map.of("id", 1, "city", "Pune")));
        when(clients.cart(anyLong())).thenAnswer(a -> new CartSnapshot(a.getArgument(0), 1L, Map.of(1L, 2)));
        when(clients.reserve(anyString(), anyLong(), anyMap())).thenAnswer(a -> new Reservation(a.getArgument(0), "RESERVED", List.of(new Item(1L, "Phone", new BigDecimal("10.50"), 2))));
        when(clients.consume(anyLong(), anyLong())).thenReturn(true);
    }
    String token(long user, boolean admin) {
        return "Bearer " + Jwts.builder().subject("user" + user + "@example.com").claim("userId", user)
                .claim("roles", List.of(admin ? "ROLE_ADMIN" : "ROLE_USER")).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("change-this-development-secret-key-to-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
    ResultActions postJson(String path, Object body, long user, boolean admin) throws Exception {
        return mvc.perform(post(path).header("Authorization", token(user, admin)).contentType("application/json").content(json.writeValueAsString(body)));
    }
    Coupon coupon(int limit, String minimum) {
        return couponService.create(new CouponService.Create("SAVE10", CouponService.Type.PERCENT, new BigDecimal("10"), new BigDecimal(minimum), Instant.now().plusSeconds(3600), limit));
    }
    PurchaseOrder delivered(long user, String method) {
        PurchaseOrder o = new PurchaseOrder(); o.id = UUID.randomUUID().toString(); o.userId = user; o.addressId = 1L;
        o.keyHash = UUID.randomUUID().toString(); o.cartVersion = System.nanoTime(); o.cartJson = "{\"1\":2}"; o.addressJson = "{}";
        o.itemsJson = "[{\"productId\":1,\"name\":\"Phone\",\"price\":10.50,\"quantity\":2}]";
        o.total = new BigDecimal("21.00"); o.status = "DELIVERED"; o.paymentMethod = method;
        o.paymentStatus = method.equals("RAZORPAY") ? "PAID" : "COLLECTED";
        if (method.equals("RAZORPAY")) o.razorpayPaymentId = "pay_return1";
        o.deliveredAt = Instant.now(); o.cartCleanupDone = true; o.inventoryCommitted = true;
        return orders.saveAndFlush(o);
    }
    @Test void authoritativeDiscountIsIdempotentAndCancellationReleasesUsage() throws Exception {
        coupon(2, "10");
        var request = new Checkout(1L, PaymentMethod.CASH_ON_DELIVERY, "save10");
        var first = service.create(1L, "discount-001", request, token(1, false));
        assertThat(first.subtotal()).isEqualByComparingTo("21.00");
        assertThat(first.discount()).isEqualByComparingTo("2.10");
        assertThat(first.totalPrice()).isEqualByComparingTo("18.90");
        couponService.setActive("SAVE10", false);
        assertThat(service.create(1L, "discount-001", request, token(1, false)).id()).isEqualTo(first.id());
        assertThat(coupons.findById("SAVE10").orElseThrow().usedCount).isEqualTo(1);
        assertThatThrownBy(() -> service.create(1L, "discount-001", new Checkout(1L, PaymentMethod.CASH_ON_DELIVERY), token(1, false)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        service.cancel(first.id(), 1L); service.cancel(first.id(), 1L);
        assertThat(coupons.findById("SAVE10").orElseThrow().usedCount).isZero();
        postJson("/api/coupons/quote", Map.of("code", "SAVE10", "subtotal", 100), 1, false).andExpect(status().isConflict());
    }
    @Test void invalidMinimumSpendFailsDurablyAndReleasesReservedStock() {
        coupon(2, "100");
        var result = service.create(1L, "minimum-001", new Checkout(1L, PaymentMethod.CASH_ON_DELIVERY, "SAVE10"), token(1, false));
        assertThat(result.status()).isEqualTo("FAILED");
        verify(clients).release(result.id());
        assertThat(coupons.findById("SAVE10").orElseThrow().usedCount).isZero();
        assertThat(service.create(1L, "minimum-001", new Checkout(1L, PaymentMethod.CASH_ON_DELIVERY, "SAVE10"), token(1, false)).status()).isEqualTo("FAILED");
    }
    @Test void concurrentOrdersCannotExceedCouponLimit() throws Exception {
        coupon(1, "0");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); try { return service.create(1L, "coupon-race-1", new Checkout(1L, PaymentMethod.CASH_ON_DELIVERY, "SAVE10"), token(1, false)).status(); } catch (CouponRejectedException ex) { return "REJECTED"; } });
            var b = pool.submit(() -> { start.await(); try { return service.create(2L, "coupon-race-2", new Checkout(1L, PaymentMethod.CASH_ON_DELIVERY, "SAVE10"), token(2, false)).status(); } catch (CouponRejectedException ex) { return "REJECTED"; } });
            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsOnlyOnce("CONFIRMED");
        }
        assertThat(coupons.findById("SAVE10").orElseThrow().usedCount).isEqualTo(1);
    }
    @Test void expiredCouponsAndInvalidAdminInputAreRejected() throws Exception {
        Coupon c = coupon(3, "0"); c.expiresAt = Instant.now().minusSeconds(1); coupons.saveAndFlush(c);
        postJson("/api/coupons/quote", Map.of("code", "SAVE10", "subtotal", 21), 1, false).andExpect(status().isConflict());
        postJson("/api/admin/coupons", Map.of("code", "BAD", "type", "PERCENT", "value", 101, "minimumSpend", 0, "expiresAt", Instant.now().plusSeconds(600).toString(), "usageLimit", 1), 1, true).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/coupons").header("Authorization", token(1, false))).andExpect(status().isForbidden());
    }
    @Test void onlyOwnerOfDeliveredPurchaseCanReviewAndPublicSummaryDoesNotLeakUserIds() throws Exception {
        var order = delivered(1L, "CASH_ON_DELIVERY");
        var body = Map.of("productId", 1, "rating", 4, "comment", "Useful everyday phone");
        postJson("/api/orders/" + order.id + "/reviews", body, 2, false).andExpect(status().isNotFound());
        postJson("/api/orders/" + order.id + "/reviews", Map.of("productId", 2, "rating", 4, "comment", "Not purchased"), 1, false).andExpect(status().isBadRequest());
        postJson("/api/orders/" + order.id + "/reviews", body, 1, false).andExpect(status().isCreated());
        postJson("/api/orders/" + order.id + "/reviews", body, 1, false).andExpect(status().isConflict());
        mvc.perform(get("/api/reviews/products/1")).andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.reviewCount").value(1)).andExpect(jsonPath("$.reviews.content[0].userId").doesNotExist());
        var other = delivered(2L, "CASH_ON_DELIVERY"); other.deliveredAt = null; other.status = "SHIPPED"; orders.saveAndFlush(other);
        postJson("/api/orders/" + other.id + "/reviews", body, 2, false).andExpect(status().isConflict());
    }
    @Test void wishlistsArePrivateAndSavingTwiceCreatesOneEntry() throws Exception {
        mvc.perform(put("/api/wishlist/1")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/wishlist/1").header("Authorization", token(1, false))).andExpect(status().isOk());
        mvc.perform(put("/api/wishlist/1").header("Authorization", token(1, false))).andExpect(status().isOk());
        mvc.perform(get("/api/wishlist").header("Authorization", token(1, false))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(delete("/api/wishlist/1").header("Authorization", token(2, false))).andExpect(status().isNoContent());
        mvc.perform(get("/api/wishlist").header("Authorization", token(2, false))).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(wishlist.count()).isEqualTo(1);
    }
    @Test void returnsEnforceOwnershipWindowApprovalAndReceiptBeforeRefund() throws Exception {
        var o = delivered(1L, "CASH_ON_DELIVERY");
        postJson("/api/orders/" + o.id + "/returns", Map.of("reason", "Does not fit"), 2, false).andExpect(status().isNotFound());
        postJson("/api/admin/orders/" + o.id + "/return/receive", Map.of("restock", true), 1, false).andExpect(status().isForbidden());
        postJson("/api/admin/orders/" + o.id + "/return/receive", Map.of("restock", true), 1, true).andExpect(status().isConflict());
        postJson("/api/orders/" + o.id + "/returns", Map.of("reason", "Does not fit"), 1, false).andExpect(status().isOk());
        lifecycle.decideReturn(o.id, new DecideReturn(ReturnDecision.APPROVED, "Please return all items"));
        assertThat(orders.findById(o.id).orElseThrow().refundStatus).isEqualTo("NONE");
        postJson("/api/admin/orders/" + o.id + "/return/receive", Map.of("restock", true), 1, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED")).andExpect(jsonPath("$.refund.status").value("MANUAL_REQUIRED"));
        postJson("/api/admin/orders/" + o.id + "/return/receive", Map.of("restock", true), 1, true).andExpect(status().isOk());
        verify(clients, times(1)).returnInventory(o.id, true);
        postJson("/api/admin/orders/" + o.id + "/refund/manual", Map.of("reference", "BANK-123"), 1, true).andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        postJson("/api/admin/orders/" + o.id + "/refund/manual", Map.of("reference", "BANK-123"), 1, true).andExpect(status().isOk());
        var old = delivered(2L, "CASH_ON_DELIVERY"); old.deliveredAt = Instant.now().minus(Duration.ofDays(31)); orders.saveAndFlush(old);
        postJson("/api/orders/" + old.id + "/returns", Map.of("reason", "Too late"), 2, false).andExpect(status().isConflict());
    }
    @Test void onlineReturnRefundUsesPaidTotalAndReceiptRecoversAfterAnOutage() throws Exception {
        var o = delivered(1L, "RAZORPAY"); o.total = new BigDecimal("18.90"); o.subtotal = new BigDecimal("21.00"); o.discount = new BigDecimal("2.10"); orders.saveAndFlush(o);
        lifecycle.requestReturn(o.id, 1L, new ReturnRequest("Damaged"));
        lifecycle.decideReturn(o.id, new DecideReturn(ReturnDecision.APPROVED, "Approved"));
        doThrow(org.mockito.Mockito.mock(feign.FeignException.class)).doNothing().when(clients).returnInventory(o.id, false);
        when(razorpay.createRefund(eq("pay_return1"), eq(1890L), anyString())).thenReturn(json.valueToTree(Map.of("id", "rfnd_return1", "payment_id", "pay_return1", "amount", 1890, "currency", "INR", "status", "processed")));
        lifecycle.startReceiveReturn(o.id, false); service.advance(o.id);
        assertThat(orders.findById(o.id).orElseThrow().status).isEqualTo("RETURN_RECEIVING");
        verify(razorpay, never()).createRefund(anyString(), anyLong(), anyString());
        service.advance(o.id); service.advance(o.id);
        assertThat(orders.findById(o.id).orElseThrow().paymentStatus).isEqualTo("REFUNDED");
        verify(razorpay, times(1)).createRefund(eq("pay_return1"), eq(1890L), anyString());
    }
    @Test void rejectedReturnCannotBeReceivedAndAdminListingIsProtected() throws Exception {
        var o = delivered(1L, "CASH_ON_DELIVERY");
        lifecycle.requestReturn(o.id, 1L, new ReturnRequest("Changed mind"));
        lifecycle.decideReturn(o.id, new DecideReturn(ReturnDecision.REJECTED, "Outside item policy"));
        postJson("/api/admin/orders/" + o.id + "/return/receive", Map.of("restock", true), 1, true).andExpect(status().isConflict());
        mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/orders").header("Authorization", token(1, false))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/orders").header("Authorization", token(1, true))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
}
