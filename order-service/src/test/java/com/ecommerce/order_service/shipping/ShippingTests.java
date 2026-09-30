package com.ecommerce.order_service.shipping;
import com.ecommerce.order_service.model.*;
import com.ecommerce.order_service.service.OrderLifecycle;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static com.ecommerce.order_service.shipping.ShippingDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ShippingTests {
    @Autowired OrderRepository orders;
    @Autowired ShipmentRepository shipments;
    @Autowired ShippingService shipping;
    @Autowired OrderLifecycle lifecycle;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @MockitoBean ShippingProvider provider;
    @BeforeEach void setup() {
        shipments.deleteAll();
        when(provider.create(any(),any(),any())).thenReturn(new Created("remote-order","remote-shipment",null,"Carrier"));
        when(provider.assign(any())).thenReturn(new Assigned("AWB123","Carrier"));
        when(provider.track(any())).thenReturn("Delivered");
        when(provider.label(any())).thenReturn(new Label("AVAILABLE","https://example.com/label.pdf","Provider link"));
    }
    PurchaseOrder order(String status) {
        PurchaseOrder o=new PurchaseOrder();o.id=UUID.randomUUID().toString();o.userId=1L;o.addressId=1L;
        o.keyHash=UUID.randomUUID().toString();o.cartVersion=System.nanoTime();o.cartJson="{}";
        o.addressJson="{\"line1\":\"Test road\",\"city\":\"Delhi\",\"state\":\"Delhi\",\"country\":\"India\",\"postalCode\":\"110001\"}";
        o.itemsJson="[{\"productId\":1,\"name\":\"Test product\",\"quantity\":1,\"price\":100}]";
        o.total=new BigDecimal("90");o.subtotal=new BigDecimal("100");o.discount=BigDecimal.TEN;
        o.paymentMethod="CASH_ON_DELIVERY";o.status=status;
        return orders.saveAndFlush(o);
    }
    Booking request(Provider p) { return new Booking(p,"Test recipient","+919999999999","test@example.invalid",new BigDecimal("0.5"),BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,"1234",null); }
    String token(long id,boolean admin) {
        return "Bearer "+Jwts.builder().subject("test").claim("userId",id).claim("roles",List.of(admin?"ROLE_ADMIN":"ROLE_USER"))
            .expiration(Date.from(Instant.now().plusSeconds(600))).signWith(Keys.hmacShaKeyFor("change-this-development-secret-key-to-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
    @Test void bookingIsIdempotentAndProviderCannotBeChanged() {
        var o=order("PROCESSING");var r=request(Provider.SHIPROCKET);
        assertThat(shipping.book(o.id,Direction.FORWARD,r).status()).isEqualTo("BOOKED");
        assertThat(shipping.book(o.id,Direction.FORWARD,r).awb()).isEqualTo("AWB123");
        verify(provider,times(1)).create(any(),any(),any()); verify(provider,times(1)).assign(any());
        assertThatThrownBy(()->shipping.book(o.id,Direction.FORWARD,request(Provider.DELHIVERY))).hasMessageContaining("409");
        assertThat(orders.findById(o.id).orElseThrow().trackingNumber).isEqualTo("AWB123");
    }
    @Test void unknownCreationNeverCreatesAgainAndBlocksCancellation() {
        var o=order("CONFIRMED");when(provider.create(any(),any(),any())).thenThrow(new IllegalStateException("lost response"));
        assertThat(shipping.book(o.id,Direction.FORWARD,request(Provider.DELHIVERY)).status()).isEqualTo("UNKNOWN");
        assertThat(shipping.book(o.id,Direction.FORWARD,request(Provider.DELHIVERY)).status()).isEqualTo("UNKNOWN");
        verify(provider,times(1)).create(any(),any(),any());
        assertThatThrownBy(()->lifecycle.cancel(o.id,1L)).hasMessageContaining("409");
    }
    @Test void lostAwbResponsePreservesProviderIds() {
        var o=order("CONFIRMED");when(provider.assign(any())).thenThrow(new IllegalStateException("timeout"));
        var view=shipping.book(o.id,Direction.FORWARD,request(Provider.SHIPROCKET));
        assertThat(view.status()).isEqualTo("UNKNOWN");assertThat(view.shipmentId()).isEqualTo("remote-shipment");
        shipping.book(o.id,Direction.FORWARD,request(Provider.SHIPROCKET));verify(provider,times(1)).assign(any());
    }
    @Test void unpaidAndUnapprovedOrdersCannotBook() {
        var o=order("CONFIRMED");o.paymentMethod="RAZORPAY";orders.saveAndFlush(o);
        assertThatThrownBy(()->shipping.book(o.id,Direction.FORWARD,request(Provider.SHIPROCKET))).hasMessageContaining("409");
        var delivered=order("DELIVERED");
        assertThatThrownBy(()->shipping.book(delivered.id,Direction.RETURN,request(Provider.DELHIVERY))).hasMessageContaining("409");
        verifyNoInteractions(provider);
    }
    @Test void trackingDoesNotCollectCashOrReceiveReturns() {
        var o=order("DELIVERED");o.returnStatus="APPROVED";orders.saveAndFlush(o);
        shipping.book(o.id,Direction.RETURN,request(Provider.DELHIVERY));
        assertThat(shipping.refresh(o.id,Direction.RETURN).trackingStatus()).isEqualTo("Delivered");
        var saved=orders.findById(o.id).orElseThrow();
        assertThat(saved.returnStatus).isEqualTo("APPROVED");assertThat(saved.paymentStatus).isEqualTo("UNPAID");assertThat(saved.status).isEqualTo("DELIVERED");
    }
    @Test void adminOnlyMutationsAndOwnerOnlyReads() throws Exception {
        var o=order("CONFIRMED");String path="/api/admin/orders/"+o.id+"/shipments/FORWARD";
        mvc.perform(post(path).contentType("application/json").content(json.writeValueAsString(request(Provider.SHIPROCKET)))).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization",token(1,false)).contentType("application/json").content(json.writeValueAsString(request(Provider.SHIPROCKET)))).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization",token(1,true)).contentType("application/json").content(json.writeValueAsString(request(Provider.SHIPROCKET)))).andExpect(status().isOk()).andExpect(jsonPath("status").value("BOOKED"));
        mvc.perform(get("/api/orders/"+o.id+"/shipments").header("Authorization",token(2,false))).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/"+o.id+"/shipments").header("Authorization",token(1,false))).andExpect(status().isOk());
        mvc.perform(post(path+"/tracking").header("Authorization",token(1,false))).andExpect(status().isForbidden());
        mvc.perform(post(path+"/label").header("Authorization",token(1,false))).andExpect(status().isForbidden());
    }
    @Test void configurationFailureDoesNotPersistAttempt() {
        var o=order("CONFIRMED");doThrow(new IllegalStateException("missing credentials")).when(provider).validate(any(),any(),any(),any());
        assertThatThrownBy(()->shipping.book(o.id,Direction.FORWARD,request(Provider.DELHIVERY))).isInstanceOf(IllegalStateException.class);
        assertThat(shipments.findByOrderIdOrderByCreatedAtAsc(o.id)).isEmpty();
        assertThat(orders.findById(o.id).orElseThrow().providerShipmentRequested).isFalse();
    }
    @Test void failedTrackingKeepsLastGoodStatus() {
        var o=order("CONFIRMED");shipping.book(o.id,Direction.FORWARD,request(Provider.SHIPROCKET));shipping.refresh(o.id,Direction.FORWARD);
        when(provider.track(any())).thenThrow(new IllegalStateException("down"));
        assertThatThrownBy(()->shipping.refresh(o.id,Direction.FORWARD)).hasMessageContaining("502");
        assertThat(shipping.list(o.id,1L,false).getFirst().trackingStatus()).isEqualTo("Delivered");
    }
    @Test void unknownPickupIsNotSentAgain() {
        var o=order("CONFIRMED");shipping.book(o.id,Direction.FORWARD,request(Provider.DELHIVERY));
        when(provider.pickup(any(),any())).thenThrow(new IllegalStateException("lost pickup response"));
        var r=new Pickup(java.time.LocalDate.now().plusDays(1),"12:00:00");
        assertThat(shipping.pickup(o.id,Direction.FORWARD,r).pickupStatus()).isEqualTo("UNKNOWN");
        shipping.pickup(o.id,Direction.FORWARD,r);verify(provider,times(1)).pickup(any(),any());
        assertThatThrownBy(()->shipping.pickup(o.id,Direction.FORWARD,new Pickup(r.date(),"13:00:00"))).hasMessageContaining("409");
    }
    @Test void concurrentDuplicateBookingCallsProviderOnlyOnce() throws Exception {
        var o=order("CONFIRMED");var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        when(provider.create(any(),any(),any())).thenAnswer(a->{entered.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("timeout");return new Created("one","one","AWB123","Carrier");});
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var first=executor.submit(()->shipping.book(o.id,Direction.FORWARD,request(Provider.DELHIVERY)));
            assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(shipping.book(o.id,Direction.FORWARD,request(Provider.DELHIVERY)).status()).isEqualTo("BOOKING");
            release.countDown();assertThat(first.get(10,java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo("BOOKED");
            verify(provider,times(1)).create(any(),any(),any());
        } finally { release.countDown();executor.shutdownNow(); }
    }
}
