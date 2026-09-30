package com.ecommerce.order_service.shipping;
import com.ecommerce.order_service.model.PurchaseOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static com.ecommerce.order_service.shipping.ShippingDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CarrierProvidersTests {
    final ShiprocketApi sr=mock(ShiprocketApi.class);
    final DelhiveryApi dl=mock(DelhiveryApi.class);
    final ObjectMapper json=new ObjectMapper();
    MockEnvironment env() {
        var e=new MockEnvironment().withProperty("shipping.live-enabled","true").withProperty("shipping.shiprocket.token","sr-test-placeholder")
            .withProperty("shipping.delhivery.token","dl-test-placeholder").withProperty("shipping.shiprocket.pickup-location","warehouse")
            .withProperty("shipping.delhivery.pickup-location","warehouse").withProperty("shipping.delhivery.client-name","client")
            .withProperty("shipping.shiprocket.channel-id","1");
        for(String key:new String[]{"name","phone","email","address","city","state","pincode","gstin"}) e.setProperty("shipping.warehouse."+key,"fixture");
        return e;
    }
    PurchaseOrder order() {
        var o=new PurchaseOrder();o.id="test-order";o.total=new BigDecimal("90");o.subtotal=new BigDecimal("100");o.discount=BigDecimal.TEN;o.paymentMethod="CASH_ON_DELIVERY";
        o.addressJson="{\"line1\":\"A&B road\",\"city\":\"Delhi\",\"state\":\"Delhi\",\"postalCode\":\"110001\",\"country\":\"India\"}";
        o.itemsJson="[{\"productId\":1,\"name\":\"Product\",\"quantity\":1,\"price\":100}]";return o;
    }
    Booking request() { return new Booking(Provider.SHIPROCKET,"Test","+919999999999","test@example.invalid",new BigDecimal("0.5"),BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,"1234",null); }
    Shipment row(Provider p,Mode m,Direction d) {var s=new Shipment();s.id="test";s.orderId="test-order";s.provider=p;s.mode=m;s.direction=d;s.shipmentId="123";s.awb="AWB123";return s;}
    @Test void mockNeverContactsProvidersAndNeverReturnsRealLabel() {
        var p=new CarrierProviders(sr,dl,json,new MockEnvironment());var row=row(Provider.SHIPROCKET,Mode.MOCK,Direction.RETURN);
        assertThat(p.create(row,order(),request()).awb()).startsWith("MOCK-");
        assertThat(p.label(row).status()).isEqualTo("SIMULATED");assertThat(p.label(row).url()).isNull();
        assertThat(p.track(row)).isEqualTo("SIMULATED_BOOKED");verifyNoInteractions(sr,dl);
    }
    @Test void stagingCannotContactShiprocketAndLiveNeedsOptIn() {
        var p=new CarrierProviders(sr,dl,json,new MockEnvironment());
        assertThatThrownBy(()->p.validate(Provider.SHIPROCKET,Mode.STAGING,order(),request())).hasMessageContaining("503");
        assertThatThrownBy(()->p.create(row(Provider.SHIPROCKET,Mode.LIVE,Direction.FORWARD),order(),request())).hasMessageContaining("503");verifyNoInteractions(sr,dl);
    }
    @Test void stagingRejectsProductionDelhiveryOrigin() {
        var p=new CarrierProviders(sr,dl,json,env().withProperty("shipping.delhivery.base-url","https://track.delhivery.com"));
        assertThatThrownBy(()->p.validate(Provider.DELHIVERY,Mode.STAGING,order(),request())).hasMessageContaining("503");
    }
    @Test void shiprocketForwardUsesServerMoneyAndAddress() throws Exception {
        var p=new CarrierProviders(sr,dl,json,env());var row=row(Provider.SHIPROCKET,Mode.LIVE,Direction.FORWARD);
        when(sr.create(anyString(),anyMap())).thenReturn(json.readTree("{\"order_id\":12,\"shipment_id\":34}"));
        var result=p.create(row,order(),request());assertThat(result.shipmentId()).isEqualTo("34");
        var payload=p.shiprocketPayload(row,order(),request());assertThat(payload).containsEntry("sub_total",new BigDecimal("100")).containsEntry("total_discount",BigDecimal.TEN).containsEntry("billing_address","A&B road").containsEntry("payment_method","COD");
    }
    @Test void shiprocketReturnReversesAddressesAndAssignsReturnAwb() throws Exception {
        var p=new CarrierProviders(sr,dl,json,env());var row=row(Provider.SHIPROCKET,Mode.LIVE,Direction.RETURN);
        var payload=p.shiprocketPayload(row,order(),request());assertThat(payload).containsEntry("pickup_address","A&B road").containsEntry("shipping_address","fixture").containsEntry("payment_method","PREPAID");
        when(sr.assign(anyString(),anyMap())).thenReturn(json.readTree("{\"awb_assign_status\":1,\"response\":{\"data\":{\"awb_code\":\"AWB123\",\"courier_name\":\"Carrier\"}}}"));
        assertThat(p.assign(row).awb()).isEqualTo("AWB123");verify(sr).assign(anyString(),argThat(b->Integer.valueOf(1).equals(b.get("is_return"))));
    }
    @Test void delhiveryFormIsEncodedAndReverseDoesNotCollectCod() throws Exception {
        var p=new CarrierProviders(sr,dl,json,env());var row=row(Provider.DELHIVERY,Mode.STAGING,Direction.RETURN);
        when(dl.create(anyString(),anyString())).thenAnswer(a->{String form=a.getArgument(1);assertThat(form).startsWith("format=json&data=");var body=json.readTree(URLDecoder.decode(form.substring(17),StandardCharsets.UTF_8));
            assertThat(body.path("shipments").get(0).path("payment_mode").asText()).isEqualTo("Pickup");
            assertThat(body.path("shipments").get(0).path("cod_amount").asInt()).isZero();
            assertThat(body.path("shipments").get(0).path("weight").asInt()).isEqualTo(500);
            return json.readTree("{\"success\":true,\"packages\":[{\"status\":\"Success\",\"waybill\":\"AWB123\"}]}");});
        assertThat(p.create(row,order(),request()).awb()).isEqualTo("AWB123");
        assertThat(p.label(row).status()).isEqualTo("NOT_REQUIRED");verify(dl,never()).label(anyString(),anyString(),anyString());
    }
    @Test void provider200FailureAndWrongTrackingIdentityAreRejected() throws Exception {
        var p=new CarrierProviders(sr,dl,json,env());
        when(sr.assign(anyString(),anyMap())).thenReturn(json.readTree("{\"awb_assign_status\":0}"));
        assertThatThrownBy(()->p.assign(row(Provider.SHIPROCKET,Mode.LIVE,Direction.FORWARD))).isInstanceOf(IllegalStateException.class);
        when(dl.track(anyString(),anyString())).thenReturn(json.readTree("{\"ShipmentData\":[{\"Shipment\":{\"AWB\":\"FOREIGN\",\"Status\":{\"Status\":\"Delivered\"}}}]}"));
        assertThatThrownBy(()->p.track(row(Provider.DELHIVERY,Mode.STAGING,Direction.FORWARD))).hasMessageContaining("identity");
    }
}
