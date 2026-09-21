package com.ecommerce.order_service.commerce;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class CouponController {
    private final CouponService coupons;
    public CouponController(CouponService coupons) { this.coupons = coupons; }
    public record QuoteRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,40}") String code,
            @NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal subtotal) {}
    public record Active(@NotNull Boolean active) {}
    @PostMapping("/api/coupons/quote")
    public CouponService.Quote quote(@Valid @RequestBody QuoteRequest request) { return coupons.quote(request.code(), request.subtotal()); }
    @PostMapping("/api/admin/coupons") @PreAuthorize("hasRole('ADMIN')")
    public Coupon create(@Valid @RequestBody CouponService.Create request) { return coupons.create(request); }
    @GetMapping("/api/admin/coupons") @PreAuthorize("hasRole('ADMIN')")
    public Page<Coupon> list(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return coupons.list(page, size); }
    @PatchMapping("/api/admin/coupons/{code}") @PreAuthorize("hasRole('ADMIN')")
    public Coupon active(@PathVariable String code, @Valid @RequestBody Active request) { return coupons.setActive(code, request.active()); }
}
