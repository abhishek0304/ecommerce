package com.ecommerce.product_service;

import com.ecommerce.product_service.entity.Product;
import com.ecommerce.product_service.repository.ProductRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Value("${security.jwt.secret}") String secret;
    Long id;
    String sku;
    @BeforeEach void fixture() {
        sku = "SECURITY-" + UUID.randomUUID();
        Product product = new Product();
        product.setSku(sku); product.setName("Original"); product.setPrice(new BigDecimal("10.00"));
        product.setStockQuantity(5); product.setActive(true);
        id = products.saveAndFlush(product).getId();
    }
    String token(String role, String signingSecret, Instant expiration) {
        var builder = Jwts.builder().subject("test@example.com").claim("userId", 1L);
        if (role != null) builder.claim("roles", List.of(role));
        if (expiration != null) builder.expiration(Date.from(expiration));
        return "Bearer " + builder.signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8))).compact();
    }
    String token(String role) { return token(role, secret, Instant.now().plusSeconds(600)); }
    String body(String sku, String name) throws Exception {
        return json.writeValueAsString(Map.of("sku", sku, "name", name, "price", 20, "stockQuantity", 10, "active", true));
    }
    List<MockHttpServletRequestBuilder> writes() throws Exception {
        return List.of(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body("NEW-" + sku, "New")),
                put("/api/v1/products/" + id).contentType(MediaType.APPLICATION_JSON).content(body(sku, "Changed")),
                delete("/api/v1/products/" + id));
    }
    @Test void anonymousWritesAreUnauthorizedButBrowsingRemainsPublic() throws Exception {
        long before = products.count();
        for (var request : writes()) mvc.perform(request).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Original"));
        mvc.perform(head("/api/v1/products/" + id)).andExpect(status().isOk());
        assertThat(products.count()).isEqualTo(before);
    }
    @Test void customerAndRolelessTokensCannotModifyProducts() throws Exception {
        for (String role : new String[]{"ROLE_USER", null, "ADMIN"}) {
            for (var request : writes()) mvc.perform(request.header("Authorization", token(role))).andExpect(status().isForbidden());
        }
        assertThat(products.findById(id).orElseThrow().getName()).isEqualTo("Original");
        mvc.perform(get("/api/v1/products/" + id).header("Authorization", token("ROLE_USER"))).andExpect(status().isOk());
    }
    @Test void invalidExpiredAndUnboundedTokensAreRejected() throws Exception {
        for (String bearer : List.of("Bearer invalid",
                token("ROLE_ADMIN", secret, Instant.now().minusSeconds(60)),
                token("ROLE_ADMIN", "a-different-secret-key-with-more-than-thirty-two-bytes", Instant.now().plusSeconds(600)),
                token("ROLE_ADMIN", secret, null))) {
            for (var request : writes()) mvc.perform(request.header("Authorization", bearer)).andExpect(status().isUnauthorized());
        }
        assertThat(products.findById(id).orElseThrow().getName()).isEqualTo("Original");
    }
    @Test void adminCanCreateEditAndDeleteProducts() throws Exception {
        String bearer = token("ROLE_ADMIN");
        String response = mvc.perform(post("/api/v1/products").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body("ADMIN-" + sku, "Created"))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long createdId = json.readTree(response).path("id").asLong();
        mvc.perform(put("/api/v1/products/" + createdId).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body("ADMIN-" + sku, "Edited"))).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Edited"));
        mvc.perform(delete("/api/v1/products/" + createdId).header("Authorization", bearer)).andExpect(status().isNoContent());
        assertThat(products.existsById(createdId)).isFalse();
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body("ANON-" + sku, "Blocked")))
                .andExpect(status().isUnauthorized());
    }
    @Test void internalServiceKeyCannotBypassProductAdminChecksAndInventoryStillWorks() throws Exception {
        String key = "local-development-service-key-change-me";
        for (var request : writes()) mvc.perform(request.header("X-Service-Key", key)).andExpect(status().isUnauthorized());
        String path = "/internal/inventory/reservations/" + UUID.randomUUID();
        mvc.perform(put(path).header("Authorization", token("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1,\"items\":{\"" + id + "\":1}}")).andExpect(status().isUnauthorized());
        mvc.perform(put(path).header("X-Service-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1,\"items\":{\"" + id + "\":1}}")).andExpect(status().isOk());
        mvc.perform(delete(path).header("X-Service-Key", key)).andExpect(status().isNoContent());
        assertThat(products.findById(id).orElseThrow().getStockQuantity()).isEqualTo(5);
    }
}
