package com.ecommerce.order_service.service;

import com.ecommerce.order_service.api.OrderDtos.*;
import com.ecommerce.order_service.client.*;
import com.ecommerce.order_service.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import feign.FeignException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orders;
    private final ServiceClients services;
    private final RazorpayClient razorpay;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final OrderLifecycle lifecycle;
    private final com.ecommerce.order_service.commerce.CouponService coupons;
    private final com.ecommerce.order_service.events.OrderEvents events;
    public OrderService(OrderRepository orders, ServiceClients services, RazorpayClient razorpay,
            ObjectMapper json, PlatformTransactionManager transactions, OrderLifecycle lifecycle, com.ecommerce.order_service.events.OrderEvents events,
            com.ecommerce.order_service.commerce.CouponService coupons) {
        this.orders = orders; this.services = services; this.razorpay = razorpay; this.json = json;
        tx = new TransactionTemplate(transactions);
        this.lifecycle = lifecycle; this.events = events;
        this.coupons = coupons;
    }

    public OrderView create(Long userId, String key, Checkout request, String bearer) {
        String hash = hash(key);
        PurchaseOrder order = orders.findByUserIdAndKeyHash(userId, hash).orElse(null);
        if (order == null) {
            coupons.validate(request.couponCode());
            if (request.paymentMethod() == PaymentMethod.RAZORPAY) razorpay.requireConfigured();
            JsonNode address;
            CartSnapshot cart;
            try {
                address = services.address(request.addressId(), bearer);
                cart = services.cart(userId);
            } catch (FeignException ex) {
                // Another request may have committed this key and consumed the cart while we fetched it.
                if (orders.findByUserIdAndKeyHash(userId, hash).isPresent()) return create(userId, key, request, bearer);
                throw ex;
            }
            if (cart == null || !userId.equals(cart.userId()) || cart.version() == null || cart.items() == null
                    || cart.items().isEmpty() || cart.items().size() > 100
                    || cart.items().entrySet().stream().anyMatch(e -> e.getKey() == null || e.getKey() <= 0 || e.getValue() == null || e.getValue() <= 0))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart must contain between 1 and 100 valid items");
            PurchaseOrder fresh = new PurchaseOrder();
            fresh.id = UUID.randomUUID().toString(); fresh.userId = userId; fresh.keyHash = hash;
            fresh.addressId = request.addressId(); fresh.paymentMethod = request.paymentMethod().name();
            fresh.couponCode = com.ecommerce.order_service.commerce.CouponService.normalize(request.couponCode());
            if (request.paymentMethod() == PaymentMethod.RAZORPAY) fresh.expiresAt = lifecycle.deadline(fresh.createdAt);
            fresh.cartVersion = cart.version(); fresh.cartJson = encode(cart.items()); fresh.addressJson = address.toString();
            try { order = orders.saveAndFlush(fresh); }
            catch (DataIntegrityViolationException ex) {
                order = orders.findByUserIdAndKeyHash(userId, hash).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.CONFLICT, "This cart snapshot already has an order. View your orders before checking out again."));
            }
        }
        if (!order.addressId.equals(request.addressId()) || !order.paymentMethod.equals(request.paymentMethod().name())
                || !Objects.equals(order.couponCode, com.ecommerce.order_service.commerce.CouponService.normalize(request.couponCode())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used with different checkout details");
        advance(order.id);
        return view(owned(order.id, userId));
    }

    public OrderView get(String id, Long userId) { return view(owned(id, userId)); }
    public Page<OrderView> adminList(int page, int size) { return orders.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending())).map(this::view); }
    public OrderView adminGet(String id) { return view(lifecycle.find(id)); }
    public Page<OrderView> list(Long userId, int page, int size) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)).map(this::view);
    }

    public void advance(String id) {
        attempt(id, () -> lifecycle.expireAndRelease(id));
        attempt(id, () -> {
            tx.executeWithoutResult(status -> {
                PurchaseOrder order = locked(id);
                order.updatedAt = Instant.now();
                if (!order.status.equals("CREATING")) return;
                Map<Long, Integer> quantities = quantities(order);
                Reservation reserved;
                try { reserved = services.reserve(id, order.userId, quantities); }
                catch (FeignException ex) {
                    if (ex.status() != 422) throw ex;
                    order.status = "FAILED";
                    order.failureReason = "A product no longer exists, is inactive, or has insufficient stock";
                    events.record(order, "OrderFailed");
                    return;
                }
                validateReservation(order, reserved, quantities);
                order.itemsJson = encode(reserved.items());
                order.total = reserved.items().stream().map(i -> i.price().multiply(BigDecimal.valueOf(i.quantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                try { coupons.apply(order); }
                catch (com.ecommerce.order_service.commerce.CouponRejectedException ex) {
                    order.failureReason = ex.getMessage();
                    order.status = "CANCELLING";
                    order.closureReason = "FAILED";
                    return;
                }
                order.status = order.paymentMethod.equals("CASH_ON_DELIVERY") ? "CONFIRMED" : "RESERVED";
                if (order.status.equals("CONFIRMED")) events.record(order, "OrderConfirmed");
            });
        });
        attempt(id, () -> preparePayment(id));
        attempt(id, () -> syncPayment(id));
        attempt(id, () -> lifecycle.expireAndRelease(id));
        attempt(id, () -> lifecycle.receiveReturn(id));
        tx.executeWithoutResult(status -> {
            PurchaseOrder order = locked(id);
            if (Set.of("CANCELLED", "EXPIRED", "FAILED").contains(order.status)) coupons.release(order);
        });
        attempt(id, () -> lifecycle.processRefund(id));
        attempt(id, () -> lifecycle.commitInventory(id));
        attempt(id, () -> cleanup(id));
    }
    private void attempt(String id, Runnable action) {
        try { action.run(); }
        catch (FeignException | ResponseStatusException ex) {
            log.warn("Order {} is waiting for a dependency; recovery will retry", id);
        }
    }

    private void preparePayment(String id) {
        boolean create = Boolean.TRUE.equals(tx.execute(status -> {
            PurchaseOrder order = locked(id);
            if (!order.status.equals("RESERVED") || lifecycle.overdue(order) || order.razorpayOrderId != null) return false;
            if (order.paymentCreationAttempted) return false;
            order.paymentCreationAttempted = true;
            return true;
        }));
        PurchaseOrder order = orders.findById(id).orElseThrow();
        if (order.razorpayOrderId != null || !(order.status.equals("RESERVED") || (lifecycle.closed(order) && order.paymentCreationAttempted))) return;
        JsonNode remote;
        try {
            remote = create ? razorpay.create(id, paise(order)) : razorpay.findByReceipt(id);
        } catch (FeignException ex) {
            // A definite client-error response rejected creation; uncertain network/server failures must only be reconciled.
            if (create && (ex.status() >= 400 && ex.status() < 500) && ex.status() != 408) {
                tx.executeWithoutResult(status -> {
                    PurchaseOrder failed = locked(id);
                    if (failed.razorpayOrderId == null && failed.status.equals("RESERVED")) {
                        failed.paymentCreationAttempted = false;
                        failed.failureReason = "Razorpay rejected payment setup. Check test credentials and amount.";
                    }
                });
            }
            throw ex;
        }
        if (remote == null) return; // Never issue a second POST after an uncertain first POST.
        tx.executeWithoutResult(status -> {
            PurchaseOrder saved = locked(id);
            if (saved.razorpayOrderId != null) return;
            if (!id.equals(remote.path("receipt").asText()) || remote.path("amount").asLong(-1) != paise(saved)
                    || !"INR".equals(remote.path("currency").asText()) || !remote.path("id").asText().matches("order_[A-Za-z0-9]+"))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay order does not match checkout");
            saved.razorpayOrderId = remote.path("id").asText();
            if (saved.status.equals("RESERVED")) {
                saved.status = "PENDING_PAYMENT";
                events.record(saved, "PaymentPending");
            }
            saved.failureReason = null;
        });
    }

    public OrderView verify(String id, Long userId, Verify request) {
        PurchaseOrder order = owned(id, userId);
        if (order.razorpayOrderId == null || !order.razorpayOrderId.equals(request.razorpay_order_id()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment does not belong to this order");
        razorpay.verifySignature(order.razorpayOrderId, request.razorpay_payment_id(), request.razorpay_signature());
        JsonNode payment = razorpay.payment(request.razorpay_payment_id());
        JsonNode remoteOrder = razorpay.order(order.razorpayOrderId);
        if (!isCaptured(order, payment) || !"paid".equals(remoteOrder.path("status").asText()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment is not captured yet. Retry verification after capture.");
        confirm(id, payment);
        advance(id);
        return get(id, userId);
    }

    public void syncPayment(String id) {
        PurchaseOrder order = orders.findById(id).orElseThrow();
        if (order.razorpayOrderId == null || !(order.status.equals("PENDING_PAYMENT") || lifecycle.closed(order))) return;
        JsonNode remoteOrder = razorpay.order(order.razorpayOrderId);
        if (remoteOrder == null || !"paid".equals(remoteOrder.path("status").asText())) return;
        JsonNode payments = razorpay.payments(order.razorpayOrderId);
        if (payments == null || !payments.path("items").isArray()) return;
        for (JsonNode payment : payments.path("items")) {
            if (isCaptured(order, payment)) { confirm(id, payment); return; }
        }
    }

    private boolean isCaptured(PurchaseOrder order, JsonNode payment) {
        return payment != null && payment.path("id").asText().matches("pay_[A-Za-z0-9]+")
                && order.razorpayOrderId.equals(payment.path("order_id").asText())
                && Set.of("captured", "refunded").contains(payment.path("status").asText())
                && payment.path("amount").asLong(-1) == paise(order)
                && "INR".equals(payment.path("currency").asText())
                && payment.path("amount_refunded").asLong(0) >= 0
                && payment.path("amount_refunded").asLong(0) <= paise(order);
    }
    private void confirm(String id, JsonNode payment) {
        tx.executeWithoutResult(status -> lifecycle.captured(locked(id), payment));
    }

    public OrderView cancel(String id, Long userId) {
        lifecycle.cancel(id, userId);
        advance(id);
        return get(id, userId);
    }
    public OrderView fulfill(String id, Fulfillment request) {
        lifecycle.fulfill(id, request);
        return view(lifecycle.find(id));
    }
    public OrderView retryRefund(String id) {
        lifecycle.retryRefund(id);
        return view(lifecycle.find(id));
    }
    private void cleanup(String id) {
        try {
            tx.executeWithoutResult(status -> {
                PurchaseOrder order = locked(id);
                if (order.cartCleanupDone || !Set.of("CONFIRMED", "PENDING_PAYMENT", "PROCESSING", "SHIPPED", "DELIVERED").contains(order.status)) return;
                order.cartCleared = services.consume(order.userId, order.cartVersion);
                order.cartCleanupDone = true;
            });
        } catch (FeignException | ResponseStatusException ex) { log.warn("Cart cleanup for order {} will retry", id); }
    }

    public void webhook(byte[] body, String signature) {
        razorpay.verifyWebhook(body, signature);
        JsonNode event = readTree(new String(body, StandardCharsets.UTF_8));
        String name = event.path("event").asText();
        if (Set.of("refund.created", "refund.processed", "refund.failed").contains(name)) {
            lifecycle.refundWebhook(event.path("payload").path("refund").path("entity").path("payment_id").asText());
            return;
        }
        if (!name.equals("payment.captured") && !name.equals("order.paid")) return;
        String providerOrderId = name.equals("order.paid")
                ? event.path("payload").path("order").path("entity").path("id").asText()
                : event.path("payload").path("payment").path("entity").path("order_id").asText();
        orders.findByRazorpayOrderId(providerOrderId).ifPresent(order -> {
            advance(order.id);
        });
    }

    private PurchaseOrder owned(String id, Long userId) {
        PurchaseOrder order = orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.userId.equals(userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        return order;
    }
    private PurchaseOrder locked(String id) { return orders.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")); }
    private long paise(PurchaseOrder order) { return order.total.movePointRight(2).longValueExact(); }
    private void validateReservation(PurchaseOrder order, Reservation reservation, Map<Long, Integer> expected) {
        if (reservation == null || !order.id.equals(reservation.id()) || !"RESERVED".equals(reservation.state())
                || reservation.items() == null || reservation.items().size() != expected.size())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid inventory response");
        Set<Long> seen = new HashSet<>();
        for (Item item : reservation.items()) {
            if (item == null || item.productId() == null || !seen.add(item.productId())
                    || !Objects.equals(expected.get(item.productId()), item.quantity())
                    || item.price() == null || item.price().signum() <= 0 || item.name() == null)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid reserved item");
        }
    }
    private OrderView view(PurchaseOrder order) {
        List<Item> items = order.itemsJson == null ? List.of() : decode(order.itemsJson, new TypeReference<List<Item>>() {});
        PaymentCheckout checkout = order.razorpayOrderId == null || !order.status.equals("PENDING_PAYMENT") ? null
                : new PaymentCheckout(razorpay.keyId(), order.razorpayOrderId, paise(order), order.currency, true);
        return new OrderView(order.id, order.status, order.paymentMethod, order.paymentStatus, items,
                order.total, order.currency, readTree(order.addressJson), order.cartCleared, order.failureReason, checkout, order.createdAt, order.expiresAt,
                new RefundView(order.refundStatus == null ? "NONE" : order.refundStatus, order.razorpayRefundId, order.refundFailure),
                order.carrier, order.trackingNumber, order.shippedAt, order.deliveredAt,
                order.subtotal == null ? order.total : order.subtotal, order.discount == null ? BigDecimal.ZERO : order.discount, order.couponCode,
                new ReturnView(order.returnStatus == null ? "NONE" : order.returnStatus, order.returnReason, order.returnDecisionNote,
                        order.returnRequestedAt, order.returnReceivedAt, order.returnRestock, order.manualRefundReference));
    }
    private Map<Long, Integer> quantities(PurchaseOrder order) { return decode(order.cartJson, new TypeReference<Map<Long, Integer>>() {}); }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private JsonNode readTree(String value) {
        try { return json.readTree(value); } catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON"); }
    }
    private <T> T decode(String value, TypeReference<T> type) {
        try { return json.readValue(value, type); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private String hash(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
