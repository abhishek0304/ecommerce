package com.ecommerce.order_service.shipping;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class ShippingDtos {
    private ShippingDtos() {}
    public enum Provider { SHIPROCKET, DELHIVERY }
    public enum Direction { FORWARD, RETURN }
    public enum Mode { MOCK, STAGING, LIVE }
    public record Booking(@NotNull Provider provider,
            @NotBlank @Size(max=100) String recipientName,
            @NotBlank @Pattern(regexp="\\+91[6-9][0-9]{9}") String recipientPhone,
            @NotBlank @Email @Size(max=254) String recipientEmail,
            @NotNull @DecimalMin("0.01") @DecimalMax("50") BigDecimal weightKg,
            @NotNull @DecimalMin("0.1") @DecimalMax("200") BigDecimal lengthCm,
            @NotNull @DecimalMin("0.1") @DecimalMax("200") BigDecimal breadthCm,
            @NotNull @DecimalMin("0.1") @DecimalMax("200") BigDecimal heightCm,
            @Pattern(regexp="[0-9]{4,8}") String hsn,
            @Pattern(regexp="[0-9]{12}") String ewaybill) {}
    public record View(String orderId, Direction direction, Provider provider, Mode mode,
            String status, String providerOrderId, String shipmentId, String awb,
            String carrier, String trackingStatus, Instant trackedAt, String failure, String pickupStatus) {}
    public record Pickup(@NotNull @FutureOrPresent java.time.LocalDate date,
            @NotBlank @Pattern(regexp="([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]") String time) {}
    public record Created(String providerOrderId, String shipmentId, String awb, String carrier) {}
    public record Assigned(String awb, String carrier) {}
    public record Label(String status, String url, String note) {}
}
