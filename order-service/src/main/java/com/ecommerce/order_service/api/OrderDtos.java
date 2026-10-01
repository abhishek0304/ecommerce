package com.ecommerce.order_service.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

public final class OrderDtos {
    private OrderDtos() {}
    public enum PaymentMethod { CASH_ON_DELIVERY, RAZORPAY }
    public enum FulfillmentStatus { PROCESSING, SHIPPED, DELIVERED }
    public record Fulfillment(@NotNull FulfillmentStatus status, @Size(max = 100) String carrier,
            @Size(max = 150) String trackingNumber, Boolean cashCollected) {}
    public record RefundView(String status, String razorpayRefundId, String failure) {}
    public record Checkout(@NotNull @Positive Long addressId, @NotNull PaymentMethod paymentMethod,
            @Pattern(regexp = "[A-Za-z0-9_-]{3,40}") String couponCode) {
        public Checkout(Long addressId, PaymentMethod paymentMethod) { this(addressId, paymentMethod, null); }
    }
    public record ReturnRequest(@NotBlank @Size(max = 1000) String reason) {}
    public enum ReturnDecision { APPROVED, REJECTED }
    public record DecideReturn(@NotNull ReturnDecision decision, @NotBlank @Size(max = 1000) String note) {}
    public record ReceiveReturn(@NotNull Boolean restock) {}
    public record ManualRefund(@NotBlank @Size(max = 150) String reference) {}
    public record ReturnView(String status, String reason, String decisionNote, Instant requestedAt, Instant receivedAt, Boolean restock, String manualRefundReference) {}
    public record Verify(@NotBlank @Pattern(regexp = "order_[A-Za-z0-9]+") String razorpay_order_id,
            @NotBlank @Pattern(regexp = "pay_[A-Za-z0-9]+") String razorpay_payment_id,
            @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String razorpay_signature) {}
    public record CartSnapshot(Long userId, Long version, Map<Long, Integer> items) {}
    public record Item(Long productId, String name, BigDecimal price, int quantity) {}
    public record Reservation(String id, String state, List<Item> items) {}
    public record PaymentCheckout(String keyId, String razorpayOrderId, long amount, String currency, boolean testMode) {}
    public record OrderView(String id, String status, String paymentMethod, String paymentStatus,
            List<Item> items, BigDecimal totalPrice, String currency, JsonNode shippingAddress,
            boolean cartCleared, String failureReason, PaymentCheckout checkout, Instant createdAt, Instant expiresAt, RefundView refund,
            String carrier, String trackingNumber, Instant shippedAt, Instant deliveredAt,
            BigDecimal subtotal, BigDecimal discount, String couponCode, ReturnView returnRequest,
            BigDecimal deliveryFee,Integer deliveryMinDays,Integer deliveryMaxDays) {}
}
