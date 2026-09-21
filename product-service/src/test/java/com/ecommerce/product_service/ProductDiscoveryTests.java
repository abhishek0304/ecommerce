package com.ecommerce.product_service;
import com.ecommerce.product_service.dto.ProductRequest;
import com.ecommerce.product_service.service.*;
import com.ecommerce.product_service.inventory.*;
import com.ecommerce.product_service.repository.ProductRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ProductDiscoveryTests {
    @Autowired MockMvc mvc;
    @Autowired ProductService products;
    @Autowired ProductRepository repository;
    @Autowired ReservationRepository reservations;
    @Autowired InventoryService inventory;
    Long low, high, hidden;
    @BeforeEach void setup() {
        reservations.deleteAll(); repository.deleteAll();
        low = create("LOW", "Travel Phone", "100.00", 3, true);
        high = create("HIGH", "Office Phone", "300.00", 0, true);
        hidden = create("HIDDEN", "Secret Phone", "150.00", 3, false);
    }
    Long create(String sku, String name, String price, int stock, boolean active) {
        return products.createProduct(new ProductRequest(sku, name, "Description", new BigDecimal(price), stock, "Electronics", active, "https://example.com/product.jpg")).id();
    }
    String token(String role) {
        return "Bearer " + Jwts.builder().subject("test@example.com").claim("userId", 1).claim("roles", List.of(role))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("test-only-secret-key-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
    @Test void filtersCombineAndPublicCatalogHidesInactiveProducts() throws Exception {
        mvc.perform(get("/api/v1/products").param("query", "PHONE").param("category", "electronics").param("minPrice", "50")
                .param("maxPrice", "200").param("inStock", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(low))
                .andExpect(jsonPath("$.content[0].imageUrl").value("https://example.com/product.jpg"));
        mvc.perform(get("/api/v1/products/" + hidden)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products/" + hidden).header("Authorization", token("ROLE_ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/admin/catalog")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/products/admin/catalog").header("Authorization", token("ROLE_USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/products/admin/catalog").header("Authorization", token("ROLE_ADMIN"))).andExpect(jsonPath("$.totalElements").value(3));
    }
    @Test void paginationSortingAndLiteralSearchAreStable() throws Exception {
        mvc.perform(get("/api/v1/products").param("sort", "price,desc").param("size", "1").param("page", "0"))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(high));
        mvc.perform(get("/api/v1/products").param("sort", "price,desc").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content[0].id").value(low));
        mvc.perform(get("/api/v1/products").param("query", "%")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/products").param("minPrice", "200").param("maxPrice", "100")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("sort", "invalid,asc")).andExpect(status().isBadRequest());
    }
    @Test void returnedStockIsRestoredOnceAndCannotBeReleasedAgain() {
        String id = UUID.randomUUID().toString();
        inventory.reserve(id, new InventoryService.Request(1L, Map.of(low, 2)));
        inventory.commit(id);
        assertThatThrownBy(() -> products.deleteProduct(low)).isInstanceOf(com.ecommerce.product_service.exception.DuplicateResourceException.class);
        inventory.receiveReturn(id, true); inventory.receiveReturn(id, true); inventory.commit(id);
        assertThat(repository.findById(low).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThatThrownBy(() -> inventory.release(id)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> inventory.receiveReturn(id, false)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void damagedReturnsDoNotRestoreStock() {
        String id = UUID.randomUUID().toString();
        inventory.reserve(id, new InventoryService.Request(1L, Map.of(low, 2))); inventory.commit(id);
        inventory.receiveReturn(id, false); inventory.receiveReturn(id, false);
        assertThat(repository.findById(low).orElseThrow().getStockQuantity()).isEqualTo(1);
    }
    @Test void unsafeImageUrlsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/products").header("Authorization", token("ROLE_ADMIN")).contentType("application/json")
                .content("{\"sku\":\"BAD\",\"name\":\"Bad\",\"price\":10,\"stockQuantity\":1,\"imageUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
    }
}
