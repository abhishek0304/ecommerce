package com.ecommerce.order_service.shipping;
import com.ecommerce.order_service.model.PurchaseOrder;
import static com.ecommerce.order_service.shipping.ShippingDtos.*;
public interface ShippingProvider {
    void validate(Provider provider, Mode mode, PurchaseOrder order, Booking request);
    Created create(Shipment shipment, PurchaseOrder order, Booking request);
    Assigned assign(Shipment shipment);
    String track(Shipment shipment);
    Label label(Shipment shipment);
    String pickup(Shipment shipment, Pickup request);
}
