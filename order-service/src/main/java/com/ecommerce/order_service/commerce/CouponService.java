package com.ecommerce.order_service.commerce;

import com.ecommerce.order_service.model.PurchaseOrder;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CouponService {
    private final CouponRepository coupons;
    public CouponService(CouponRepository coupons) { this.coupons = coupons; }
    public enum Type { PERCENT, FIXED }
    public record Create(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,40}") String code, @NotNull Type type,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal value,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal minimumSpend,
            @NotNull @Future Instant expiresAt, @Min(1) @Max(1000000) int usageLimit) {}
    public record Quote(String code, BigDecimal subtotal, BigDecimal discount, BigDecimal total) {}
    public static String normalize(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }
    @Transactional
    public Coupon create(Create request) {
        String code = normalize(request.code());
        if (request.type() == Type.PERCENT && request.value().compareTo(new BigDecimal("100")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentage cannot exceed 100");
        if (coupons.existsById(code)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon code already exists");
        Coupon c = new Coupon(); c.code = code; c.type = request.type().name(); c.value = request.value();
        c.minimumSpend = request.minimumSpend(); c.expiresAt = request.expiresAt(); c.usageLimit = request.usageLimit();
        return coupons.saveAndFlush(c);
    }
    @Transactional(readOnly = true)
    public Page<Coupon> list(int page, int size) { return coupons.findAll(PageRequest.of(page, size, Sort.by("expiresAt").descending())); }
    @Transactional
    public Coupon setActive(String code, boolean active) { Coupon c = find(code); c.active = active; return c; }
    @Transactional(readOnly = true)
    public void validate(String code) {
        if (normalize(code) != null) check(coupons.findById(normalize(code)).orElseThrow(() -> invalid("Unknown coupon")));
    }
    @Transactional(readOnly = true)
    public Quote quote(String code, BigDecimal subtotal) {
        Coupon c = coupons.findById(normalize(code)).orElseThrow(() -> invalid("Unknown coupon"));
        BigDecimal discount = discount(c, subtotal);
        return new Quote(c.code, subtotal, discount, subtotal.subtract(discount));
    }
    // Called while holding the order lock, in the same database transaction as the price snapshot.
    @Transactional(noRollbackFor = CouponRejectedException.class)
    public void apply(PurchaseOrder order) {
        order.subtotal = order.total;
        order.discount = BigDecimal.ZERO.setScale(2);
        if (order.couponCode == null) return;
        Coupon coupon = find(order.couponCode);
        order.discount = discount(coupon, order.subtotal);
        order.total = order.subtotal.subtract(order.discount);
        coupon.usedCount++;
        order.couponRedeemed = true;
    }
    @Transactional
    public void release(PurchaseOrder order) {
        if (!order.couponRedeemed) return;
        Coupon c = find(order.couponCode);
        c.usedCount = Math.max(0, c.usedCount - 1);
        order.couponRedeemed = false;
    }
    private Coupon find(String code) { return coupons.lock(normalize(code)).orElseThrow(() -> invalid("Unknown coupon")); }
    private BigDecimal discount(Coupon c, BigDecimal subtotal) {
        check(c);
        if (subtotal.compareTo(c.minimumSpend) < 0) throw invalid("Coupon minimum spend is " + c.minimumSpend);
        BigDecimal result = "PERCENT".equals(c.type) ? subtotal.multiply(c.value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP) : c.value;
        // Keep at least INR 1 payable so online and COD checkout use the same rule.
        BigDecimal max = subtotal.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        return result.min(max).setScale(2, RoundingMode.HALF_UP);
    }
    private void check(Coupon c) {
        if (!c.active || !Instant.now().isBefore(c.expiresAt)) throw invalid("Coupon is inactive or expired");
        if (c.usedCount >= c.usageLimit) throw invalid("Coupon usage limit reached");
    }
    private CouponRejectedException invalid(String detail) { return new CouponRejectedException(detail); }
}
