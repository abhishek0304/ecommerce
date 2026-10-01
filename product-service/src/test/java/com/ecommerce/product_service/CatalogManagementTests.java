package com.ecommerce.product_service;
import com.ecommerce.product_service.entity.Product;
import com.ecommerce.product_service.repository.ProductRepository;
import com.ecommerce.product_service.inventory.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class CatalogManagementTests {
    @Autowired MockMvc mvc;@Autowired ProductRepository products;@Autowired InventoryService inventory;@Autowired MovementRepository movements;@Autowired ObjectMapper json;
    @Value("${security.jwt.secret}") String secret;
    String token(String role){return "Bearer "+Jwts.builder().subject("admin@example.com").claim("userId",1L).claim("roles",List.of(role)).expiration(Date.from(Instant.now().plusSeconds(600))).signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();}
    Long product(){Product p=new Product();p.setSku("VAR-"+UUID.randomUUID());p.setName("Shirt");p.setPrice(new BigDecimal("300.00"));p.setStockQuantity(10);return products.saveAndFlush(p).getId();}
    @Test void variantsHaveIndependentInventoryAndAdminAuthorization() throws Exception {
        Long parent=product();var request=Map.of("label","Large green","size","L","color","Green","product",Map.of("sku","GREEN-"+UUID.randomUUID(),"name","Green shirt L","price",350,"stockQuantity",4,"active",true));
        mvc.perform(post("/api/v1/products/admin/"+parent+"/variants").header("Authorization",token("ROLE_USER")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isForbidden());
        var created=json.readTree(mvc.perform(post("/api/v1/products/admin/"+parent+"/variants").header("Authorization",token("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long variant=created.path("product").path("id").asLong();inventory.reserve(UUID.randomUUID().toString(),new InventoryService.Request(1L,Map.of(variant,2)));
        assertThat(products.findById(parent).orElseThrow().getStockQuantity()).isEqualTo(10);assertThat(products.findById(variant).orElseThrow().getStockQuantity()).isEqualTo(2);
        mvc.perform(get("/api/v1/products/"+variant+"/variants")).andExpect(status().isOk()).andExpect(jsonPath("$[0].label").value("Large green"));
        mvc.perform(delete("/api/v1/products/"+parent).header("Authorization",token("ROLE_ADMIN"))).andExpect(status().isConflict());
    }
    @Test void inventoryHistoryDoesNotDuplicateOnReservationRetries() throws Exception {
        Long id=product();String reservation=UUID.randomUUID().toString();var request=new InventoryService.Request(1L,Map.of(id,2));
        inventory.reserve(reservation,request);inventory.reserve(reservation,request);inventory.release(reservation);inventory.release(reservation);
        var history=movements.findByProductIdOrderByCreatedAtDesc(id,PageRequest.of(0,20));assertThat(history.getTotalElements()).isEqualTo(2);
        assertThat(history.getContent().stream().mapToInt(m->m.delta).sum()).isZero();assertThat(products.findById(id).orElseThrow().getStockQuantity()).isEqualTo(10);
        mvc.perform(post("/api/v1/products/admin/"+id+"/stock").header("Authorization",token("ROLE_ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"delta\":-11,\"reference\":\"invalid\"}")).andExpect(status().isConflict());
        assertThat(movements.findByProductIdOrderByCreatedAtDesc(id,PageRequest.of(0,20)).getTotalElements()).isEqualTo(2);
        mvc.perform(get("/api/v1/products/admin/low-stock").header("Authorization",token("ROLE_USER"))).andExpect(status().isForbidden());
    }
}
