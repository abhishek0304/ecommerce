package com.ecommerce.order_service.shipping;
import jakarta.persistence.*;
import java.time.Instant;
import static com.ecommerce.order_service.shipping.ShippingDtos.*;

@Entity @Table(name="order_shipments",uniqueConstraints=@UniqueConstraint(columnNames={"order_id","direction"}))
public class Shipment {
    @Id public String id;
    @Version public Long version;
    @Column(name="order_id",nullable=false) public String orderId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Direction direction;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Provider provider;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Mode mode;
    @Column(nullable=false,length=64) public String fingerprint;
    public String status;
    public String providerOrderId;
    public String shipmentId;
    public String awb;
    public String carrier;
    public String trackingStatus;
    public Instant trackedAt;
    public Instant nextTrackAt=Instant.now();
    public String failure;
    public String pickupStatus;
    @Column(length=64) public String pickupFingerprint;
    public Instant createdAt=Instant.now();
    public View view() { return new View(orderId,direction,provider,mode,status,providerOrderId,shipmentId,awb,carrier,trackingStatus,trackedAt,failure,pickupStatus); }
}
