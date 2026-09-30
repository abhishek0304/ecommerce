package com.ecommerce.order_service.shipping;

import com.ecommerce.order_service.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static com.ecommerce.order_service.shipping.ShippingDtos.*;

@Service
public class ShippingService {
    private final OrderRepository orders;
    private final ShipmentRepository shipments;
    private final ShippingProvider provider;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Mode mode;
    public ShippingService(OrderRepository orders, ShipmentRepository shipments, ShippingProvider provider,
            TransactionTemplate tx, ObjectMapper json, @Value("${shipping.mode:MOCK}") Mode mode) {
        this.orders=orders; this.shipments=shipments; this.provider=provider; this.tx=tx; this.json=json; this.mode=mode;
    }
    public View book(String id, Direction direction, Booking request) {
        String key=id+":"+direction;
        String hash=fingerprint(request);
        // The order lock serializes creation, cancellation and return approval.
        Shipment claimed=tx.execute(t -> {
            PurchaseOrder order=orders.lock(id).orElseThrow(ShippingService::missing);
            var old=shipments.findById(key);
            if(old.isPresent()) {
                if(!old.get().fingerprint.equals(hash)) throw conflict("Shipment already requested with different details");
                return null; // Never reissue a creation after a lost response or process crash.
            }
            if(direction==Direction.FORWARD) {
                if(!Set.of("CONFIRMED","PROCESSING").contains(order.status)) throw conflict("Forward booking requires a confirmed or processing order");
                if("RAZORPAY".equals(order.paymentMethod) && !"PAID".equals(order.paymentStatus)) throw conflict("Online payment must be captured first");
            } else if(!"DELIVERED".equals(order.status) || !"APPROVED".equals(order.returnStatus)) {
                throw conflict("Return booking requires an approved delivered-order return");
            }
            provider.validate(request.provider(),mode,order,request);
            Shipment row=new Shipment(); row.id=key; row.orderId=id; row.direction=direction; row.provider=request.provider();
            row.mode=mode; row.fingerprint=hash; row.status="BOOKING";
            if(direction==Direction.FORWARD) order.providerShipmentRequested=true;
            return shipments.saveAndFlush(row);
        });
        if(claimed==null) return get(key).view();
        try {
            Created created=provider.create(claimed,orders.findById(id).orElseThrow(ShippingService::missing),request);
            tx.executeWithoutResult(t -> {
                Shipment row=shipments.lock(key).orElseThrow(ShippingService::missing);
                row.providerOrderId=created.providerOrderId(); row.shipmentId=created.shipmentId();
                row.awb=created.awb(); row.carrier=created.carrier(); row.status="CREATED";
            });
            Shipment createdRow=get(key);
            Assigned assigned=createdRow.awb==null?provider.assign(createdRow):new Assigned(createdRow.awb,createdRow.carrier);
            tx.executeWithoutResult(t -> {
                PurchaseOrder order=orders.lock(id).orElseThrow(ShippingService::missing);
                Shipment row=shipments.lock(key).orElseThrow(ShippingService::missing);
                row.awb=assigned.awb(); row.carrier=assigned.carrier(); row.status="BOOKED";
                if(direction==Direction.FORWARD) { order.carrier=row.carrier; order.trackingNumber=row.awb; }
            });
        } catch(RuntimeException exception) {
            tx.executeWithoutResult(t -> {
                Shipment row=shipments.lock(key).orElseThrow(ShippingService::missing);
                row.status="UNKNOWN"; row.failure="Provider outcome requires reconciliation; do not rebook. Inspect the provider using the order reference.";
            });
        }
        return get(key).view();
    }
    public List<View> list(String id, Long user, boolean admin) {
        owned(id,user,admin); return shipments.findByOrderIdOrderByCreatedAtAsc(id).stream().map(Shipment::view).toList();
    }
    public View refresh(String id, Direction direction) {
        Shipment row=booked(id,direction);
        String state;
        try { state=provider.track(row); }
        catch(RuntimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Carrier tracking unavailable"); }
        tx.executeWithoutResult(t -> {
            Shipment locked=shipments.lock(row.id).orElseThrow(ShippingService::missing);
            locked.trackingStatus=state; locked.trackedAt=Instant.now();
            locked.nextTrackAt=Instant.now().plusSeconds(Set.of("delivered","cancelled","canceled").contains(state.toLowerCase(Locale.ROOT))?315360000:900);
        });
        // Tracking never attests COD cash collection, returned-item inspection or a refund.
        return get(row.id).view();
    }
    public Label label(String id, Direction direction) {
        try { return provider.label(booked(id,direction)); }
        catch(ResponseStatusException ex) { throw ex; }
        catch(RuntimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Carrier label unavailable"); }
    }
    public View pickup(String id, Direction direction, Pickup request) {
        Shipment row=booked(id,direction); String hash=fingerprint(request);
        Boolean claimed=tx.execute(t -> {
            Shipment locked=shipments.lock(row.id).orElseThrow(ShippingService::missing);
            if(locked.pickupStatus!=null) {
                if(!hash.equals(locked.pickupFingerprint)) throw conflict("Pickup already requested with different details");
                return false;
            }
            locked.pickupStatus="REQUESTING"; locked.pickupFingerprint=hash; return true;
        });
        if(Boolean.TRUE.equals(claimed)) {
            String result;
            try { result=provider.pickup(row,request); }
            catch(RuntimeException ex) { result="UNKNOWN"; }
            String saved=result;
            tx.executeWithoutResult(t->shipments.lock(row.id).orElseThrow(ShippingService::missing).pickupStatus=saved);
        }
        return get(row.id).view();
    }
    private Shipment booked(String id, Direction direction) {
        Shipment row=get(id+":"+direction);
        if(!"BOOKED".equals(row.status)) throw conflict("Booking is not complete; inspect provider reconciliation status");
        return row;
    }
    private void owned(String id,Long user,boolean admin) {
        PurchaseOrder order=orders.findById(id).orElseThrow(ShippingService::missing);
        if(!admin && !order.userId.equals(user)) throw missing();
    }
    private Shipment get(String key) { return shipments.findById(key).orElseThrow(ShippingService::missing); }
    private String fingerprint(Object request) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Order or shipment not found"); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
