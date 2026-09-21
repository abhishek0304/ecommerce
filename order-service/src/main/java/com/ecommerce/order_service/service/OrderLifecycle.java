package com.ecommerce.order_service.service;

import com.ecommerce.order_service.api.OrderDtos.Fulfillment;
import com.ecommerce.order_service.client.*;
import com.ecommerce.order_service.events.OrderEvents;
import com.ecommerce.order_service.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import feign.FeignException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderLifecycle {
    private final OrderRepository orders;
    private final ServiceClients services;
    private final RazorpayClient razorpay;
    private final OrderEvents events;
    private final TransactionTemplate tx;
    private final Duration timeout;
    public OrderLifecycle(OrderRepository orders, ServiceClients services, RazorpayClient razorpay,
            OrderEvents events, PlatformTransactionManager manager,
            @Value("${order.payment-timeout:15m}") Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Payment timeout must be positive");
        this.orders = orders; this.services = services; this.razorpay = razorpay; this.events = events;
        this.tx = new TransactionTemplate(manager); this.timeout = timeout;
    }
    public Instant deadline(Instant created) { return created.plus(timeout); }
    public boolean closed(PurchaseOrder order) {
        return Set.of("CANCELLING", "CANCELLED", "EXPIRED", "RETURNED").contains(order.status);
    }
    public boolean overdue(PurchaseOrder order) {
        return "RAZORPAY".equals(order.paymentMethod) && !Instant.now().isBefore(
                order.expiresAt == null ? deadline(order.createdAt) : order.expiresAt);
    }
    public void expireAndRelease(String id) {
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if ("RAZORPAY".equals(order.paymentMethod) && order.expiresAt == null) order.expiresAt = deadline(order.createdAt);
            if (Set.of("CREATING", "RESERVED", "PENDING_PAYMENT").contains(order.status) && overdue(order))
                close(order, "EXPIRED");
        });
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if (!order.status.equals("CANCELLING")) return;
            services.release(id); // Idempotent, including a tombstone if no reservation has arrived yet.
            order.stockReleased = true;
            order.status = "EXPIRED".equals(order.closureReason) ? "EXPIRED" : "FAILED".equals(order.closureReason) ? "FAILED" : "CANCELLED";
            events.record(order, order.status.equals("EXPIRED") ? "OrderExpired" : order.status.equals("FAILED") ? "OrderFailed" : "OrderCancelled");
            if ("PAID".equals(order.paymentStatus)) requestRefund(order);
        });
    }
    public void cancel(String id, Long userId) {
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if (!order.userId.equals(userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
            if (closed(order) || order.status.equals("FAILED")) return;
            if (!Set.of("CREATING", "RESERVED", "PENDING_PAYMENT", "CONFIRMED", "PROCESSING").contains(order.status))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Shipped or delivered orders cannot be cancelled");
            close(order, "CANCELLED");
        });
    }
    private void close(PurchaseOrder order, String reason) {
        order.closureReason = reason;
        order.status = "CANCELLING";
        if ("PAID".equals(order.paymentStatus)) requestRefund(order);
    }

    // Called while the order row is locked, in the transaction that writes its payment state.
    public void captured(PurchaseOrder order, JsonNode payment) {
        String paymentId = payment.path("id").asText();
        if (order.razorpayPaymentId != null && !order.razorpayPaymentId.equals(paymentId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already has a different payment");
        boolean first = order.razorpayPaymentId == null;
        order.razorpayPaymentId = paymentId;
        long refunded = payment.path("amount_refunded").asLong(0);
        if (refunded > 0) {
            if (!closed(order)) close(order, "CANCELLED");
            if (refunded == amount(order)) {
                setRefundState(order, "PROCESSED", null);
            } else {
                setRefundState(order, "REVIEW_REQUIRED", "An external partial refund needs reconciliation");
            }
            return;
        }
        if ("PENDING_PAYMENT".equals(order.status) && overdue(order)) close(order, "EXPIRED");
        if (first) {
            order.paymentStatus = "PAID";
            events.record(order, "PaymentCaptured");
        }
        if (closed(order)) { requestRefund(order); return; }
        if (Set.of("CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED").contains(order.status)) return;
        if (!"PENDING_PAYMENT".equals(order.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not awaiting payment");
        order.paymentStatus = "PAID";
        order.status = "CONFIRMED";
        events.record(order, "OrderConfirmed");
    }
    private void requestRefund(PurchaseOrder order) {
        if (order.razorpayPaymentId == null || (order.refundStatus != null && !"NONE".equals(order.refundStatus))) return;
        order.refundAttemptId = UUID.randomUUID().toString();
        setRefundState(order, "REQUESTED", null);
    }

    public void processRefund(String id) {
        PurchaseOrder current = orders.findById(id).orElseThrow();
        if (!closed(current) || current.razorpayPaymentId == null
                || current.refundStatus == null || Set.of("NONE", "PROCESSED", "FAILED", "REVIEW_REQUIRED").contains(current.refundStatus)) return;
        // The attempt UUID is committed before any provider call. Retries reuse the same header and body.
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if (order.refundAttemptId == null) order.refundAttemptId = UUID.randomUUID().toString();
        });
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if (Set.of("PROCESSED", "FAILED", "REVIEW_REQUIRED").contains(order.refundStatus)) return;
            JsonNode refund;
            try {
                refund = order.razorpayRefundId == null
                        ? razorpay.createRefund(order.razorpayPaymentId, amount(order), order.refundAttemptId)
                        : razorpay.refund(order.razorpayRefundId);
            } catch (FeignException ex) {
                int http = ex.status();
                if (http >= 400 && http < 500 && http != 408 && http != 409 && http != 429) {
                    setRefundState(order, "FAILED", "Razorpay rejected the refund. An admin can review and retry.");
                    return;
                }
                throw ex;
            }
            applyRefund(order, refund);
        });
    }
    private void applyRefund(PurchaseOrder order, JsonNode refund) {
        if (refund == null || !refund.path("id").asText().matches("rfnd_[A-Za-z0-9]+")
                || !order.razorpayPaymentId.equals(refund.path("payment_id").asText())
                || refund.path("amount").asLong(-1) != amount(order)
                || !"INR".equals(refund.path("currency").asText()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Refund response does not match this order");
        if (order.razorpayRefundId != null && !order.razorpayRefundId.equals(refund.path("id").asText()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund ID does not match");
        order.razorpayRefundId = refund.path("id").asText();
        switch (refund.path("status").asText()) {
            case "processed" -> setRefundState(order, "PROCESSED", null);
            case "pending" -> { if (!"PROCESSED".equals(order.refundStatus)) setRefundState(order, "PENDING", null); }
            case "failed" -> { if (!"PROCESSED".equals(order.refundStatus)) setRefundState(order, "FAILED", "Razorpay reported a failed refund"); }
            default -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unrecognized refund status");
        }
    }
    private void setRefundState(PurchaseOrder order, String state, String failure) {
        if ("PROCESSED".equals(order.refundStatus)) return;
        boolean changed = !state.equals(order.refundStatus);
        order.refundStatus = state; order.refundFailure = failure;
        order.paymentStatus = switch (state) {
            case "PROCESSED" -> "REFUNDED";
            case "FAILED", "REVIEW_REQUIRED" -> "REFUND_FAILED";
            default -> "REFUND_PENDING";
        };
        if (changed) events.record(order, switch (state) {
            case "PROCESSED" -> "RefundProcessed"; case "FAILED", "REVIEW_REQUIRED" -> "RefundFailed";
            case "REQUESTED" -> "RefundRequested"; default -> "RefundPending";
        });
    }
    public void retryRefund(String id) {
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if (!closed(order) || !"FAILED".equals(order.refundStatus))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a failed refund can be retried");
            if (order.razorpayRefundId != null) {
                JsonNode existing = razorpay.refund(order.razorpayRefundId);
                if (!"failed".equals(existing.path("status").asText())) { applyRefund(order, existing); return; }
                // A confirmed terminal failure permits a new attempt; uncertain requests keep their original key.
                order.refundAttemptId = UUID.randomUUID().toString();
                order.razorpayRefundId = null;
            }
            setRefundState(order, "REQUESTED", null);
        });
        processRefund(id);
    }
    public void refundWebhook(String paymentId) {
        orders.findByRazorpayPaymentId(paymentId).ifPresent(order -> {
            if (order.razorpayRefundId == null) { processRefund(order.id); return; }
            tx.executeWithoutResult(status -> {
                PurchaseOrder locked = lock(order.id);
                applyRefund(locked, razorpay.refund(locked.razorpayRefundId));
            });
        });
    }
    public void fulfill(String id, Fulfillment request) {
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            String target = request.status().name();
            if (target.equals(order.status)) {
                if (target.equals("SHIPPED") && (!Objects.equals(order.carrier, request.carrier())
                        || !Objects.equals(order.trackingNumber, request.trackingNumber())))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Shipping details differ from the original transition");
                return;
            }
            String expected = switch (target) { case "PROCESSING" -> "CONFIRMED"; case "SHIPPED" -> "PROCESSING"; default -> "SHIPPED"; };
            if (!expected.equals(order.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid fulfillment transition");
            if (order.paymentMethod.equals("RAZORPAY") && !order.paymentStatus.equals("PAID"))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Online payment must be captured before fulfillment");
            if (target.equals("SHIPPED")) {
                if (request.carrier() == null || request.carrier().isBlank() || request.trackingNumber() == null || request.trackingNumber().isBlank())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Carrier and trackingNumber are required");
                order.carrier = request.carrier(); order.trackingNumber = request.trackingNumber(); order.shippedAt = Instant.now();
            }
            if (target.equals("DELIVERED")) {
                if (order.paymentMethod.equals("CASH_ON_DELIVERY")) {
                    if (!Boolean.TRUE.equals(request.cashCollected())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirm cashCollected for COD delivery");
                    order.paymentStatus = "COLLECTED";
                    events.record(order, "PaymentCollected");
                }
                order.deliveredAt = Instant.now();
            }
            order.status = target;
            events.record(order, switch (target) { case "PROCESSING" -> "OrderProcessing"; case "SHIPPED" -> "OrderShipped"; default -> "OrderDelivered"; });
        });
        commitInventory(id);
    }
    public void commitInventory(String id) {
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if (!Set.of("SHIPPED", "DELIVERED").contains(order.status) || order.inventoryCommitted) return;
            services.commitInventory(id);
            order.inventoryCommitted = true;
        });
    }
    public PurchaseOrder find(String id) { return orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")); }
    public void requestReturn(String id, Long userId, com.ecommerce.order_service.api.OrderDtos.ReturnRequest request) {
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if (!order.userId.equals(userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
            if (order.returnStatus != null) {
                if (!Objects.equals(order.returnReason, request.reason().trim())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Return already requested with a different reason");
                return;
            }
            if (!"DELIVERED".equals(order.status) || order.deliveredAt == null
                    || Instant.now().isAfter(order.deliveredAt.plus(Duration.ofDays(30))))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Returns require a delivered order within 30 days");
            order.returnStatus = "REQUESTED"; order.returnReason = request.reason().trim(); order.returnRequestedAt = Instant.now();
            events.record(order, "ReturnRequested");
        });
    }
    public void decideReturn(String id, com.ecommerce.order_service.api.OrderDtos.DecideReturn request) {
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if (request.decision().name().equals(order.returnStatus) && Objects.equals(order.returnDecisionNote, request.note().trim())) return;
            if (!"REQUESTED".equals(order.returnStatus)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a requested return can be decided");
            order.returnStatus = request.decision().name(); order.returnDecisionNote = request.note().trim();
            events.record(order, "Return" + (order.returnStatus.equals("APPROVED") ? "Approved" : "Rejected"));
        });
    }
    public void startReceiveReturn(String id, boolean restock) {
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if (Set.of("RECEIVING", "RECEIVED").contains(order.returnStatus == null ? "" : order.returnStatus)) {
                if (!Objects.equals(order.returnRestock, restock)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Receipt was already recorded with a different stock decision");
                return;
            }
            if (!"APPROVED".equals(order.returnStatus)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Return must be approved before receipt");
            order.returnRestock = restock; order.returnStatus = "RECEIVING"; order.status = "RETURN_RECEIVING";
        });
    }
    public void receiveReturn(String id) {
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if (!"RETURN_RECEIVING".equals(order.status)) return;
            services.returnInventory(id, Boolean.TRUE.equals(order.returnRestock));
            order.returnStatus = "RECEIVED"; order.returnReceivedAt = Instant.now(); order.status = "RETURNED";
            order.inventoryCommitted = true;
            events.record(order, "ReturnReceived");
            if ("RAZORPAY".equals(order.paymentMethod)) requestRefund(order);
            else {
                order.refundStatus = "MANUAL_REQUIRED"; order.paymentStatus = "REFUND_PENDING";
                events.record(order, "RefundRequested");
            }
        });
    }
    public void manualRefund(String id, String reference) {
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = lock(id);
            if ("PROCESSED".equals(order.refundStatus) && Objects.equals(order.manualRefundReference, reference.trim())) return;
            if (!"RETURNED".equals(order.status) || !"CASH_ON_DELIVERY".equals(order.paymentMethod) || !"MANUAL_REQUIRED".equals(order.refundStatus))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a received COD return can have a manual refund recorded");
            order.manualRefundReference = reference.trim();
            setRefundState(order, "PROCESSED", null);
        });
    }
    private PurchaseOrder lock(String id) { return orders.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")); }
    private long amount(PurchaseOrder order) { return order.total.movePointRight(2).longValueExact(); }
}
