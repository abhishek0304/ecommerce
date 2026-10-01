package com.ecommerce.order_service;
import com.ecommerce.order_service.model.*;
import com.ecommerce.order_service.commerce.*;
import com.ecommerce.order_service.support.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class SupportAndAnalyticsTests {
    @Autowired MockMvc mvc;@Autowired OrderRepository orders;@Autowired TicketRepository tickets;@Autowired DeliveryRepository rules;@Autowired ObjectMapper json;
    @Value("${security.jwt.secret}") String secret;
    @BeforeEach void clean(){tickets.deleteAll();orders.deleteAll();rules.deleteAll();}
    @AfterEach void cleanRules(){rules.deleteAll();}
    String token(long user,String role){return "Bearer "+Jwts.builder().subject("user@example.com").claim("userId",user).claim("roles",List.of(role)).expiration(Date.from(Instant.now().plusSeconds(600))).signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();}
    PurchaseOrder order(long user,String status,String payment){var o=new PurchaseOrder();o.id=UUID.randomUUID().toString();o.userId=user;o.keyHash=UUID.randomUUID().toString();o.cartVersion=System.nanoTime();o.addressId=1L;o.paymentMethod="CASH_ON_DELIVERY";o.addressJson="{}";o.cartJson="{\"1\":2}";o.itemsJson="[{\"productId\":1,\"name\":\"Test product\",\"price\":10,\"quantity\":2}]";o.status=status;o.paymentStatus=payment;o.total=new BigDecimal("25");o.subtotal=new BigDecimal("20");o.deliveryFee=new BigDecimal("5");return orders.saveAndFlush(o);}
    @Test void ticketOwnershipAndStaffReplyAreEnforced()throws Exception {
        var order=order(1,"CONFIRMED","UNPAID");var payload=json.writeValueAsString(Map.of("orderId",order.id,"subject","Delivery question","message","When will it arrive?"));
        mvc.perform(post("/api/support").header("Authorization",token(2,"ROLE_USER")).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isNotFound());
        var result=json.readTree(mvc.perform(post("/api/support").header("Authorization",token(1,"ROLE_USER")).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String id=result.path("id").asText();
        mvc.perform(post("/api/support/"+id+"/messages").header("Authorization",token(2,"ROLE_USER")).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Not my ticket\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/support").header("Authorization",token(1,"ROLE_USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/support/"+id+"/messages").header("Authorization",token(9,"ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Your order is being processed\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.messages[1].admin").value(true));
        mvc.perform(patch("/api/admin/support/"+id).header("Authorization",token(9,"ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(get("/api/support").header("Authorization",token(2,"ROLE_USER"))).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void longestDeliveryPrefixWinsAndThresholdMakesShippingFree()throws Exception {
        for(String prefix:List.of("4","411")){var r=new DeliveryRule();r.postalPrefix=prefix;r.fee=new BigDecimal(prefix.equals("411")?"49":"99");r.freeAbove=new BigDecimal("500");r.minDays=2;r.maxDays=4;rules.save(r);}
        mvc.perform(post("/api/delivery/quote").header("Authorization",token(1,"ROLE_USER")).contentType(MediaType.APPLICATION_JSON).content("{\"postalCode\":\"411001\",\"subtotal\":100}")).andExpect(status().isOk()).andExpect(jsonPath("$.fee").value(49));
        mvc.perform(post("/api/delivery/quote").header("Authorization",token(1,"ROLE_USER")).contentType(MediaType.APPLICATION_JSON).content("{\"postalCode\":\"411001\",\"subtotal\":500}")).andExpect(jsonPath("$.fee").value(0));
        mvc.perform(put("/api/admin/delivery").header("Authorization",token(1,"ROLE_USER")).contentType(MediaType.APPLICATION_JSON).content("{\"postalPrefix\":\"1\",\"fee\":50,\"freeAbove\":500,\"minDays\":2,\"maxDays\":4,\"active\":true}")).andExpect(status().isForbidden());
    }
    @Test void analyticsDistinguishesBookedSalesFromCollectedCodAndRefunds()throws Exception {
        order(1,"DELIVERED","COLLECTED");order(2,"CONFIRMED","UNPAID");order(3,"CANCELLED","REFUNDED");
        mvc.perform(get("/api/admin/analytics").header("Authorization",token(1,"ROLE_USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/analytics").header("Authorization",token(9,"ROLE_ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.orderCount").value(3)).andExpect(jsonPath("$.bookedSales").value(50)).andExpect(jsonPath("$.collectedRevenue").value(25)).andExpect(jsonPath("$.refunds").value(25)).andExpect(jsonPath("$.cancelledOrders").value(1)).andExpect(jsonPath("$.popular[0].units").value(4));
        mvc.perform(get("/api/admin/analytics?days=1000").header("Authorization",token(9,"ROLE_ADMIN"))).andExpect(status().isBadRequest());
    }
}
