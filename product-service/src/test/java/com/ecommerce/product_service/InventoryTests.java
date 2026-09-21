package com.ecommerce.product_service;

import com.ecommerce.product_service.entity.Product;
import com.ecommerce.product_service.repository.ProductRepository;
import com.ecommerce.product_service.inventory.*;
import com.ecommerce.product_service.service.ProductService;
import com.ecommerce.product_service.exception.DuplicateResourceException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryTests {
    @Autowired InventoryService inventory;
    @Autowired ReservationRepository reservations;
    @Autowired ProductRepository products;
    @Autowired ProductService productService;
    @Autowired MockMvc mvc;
    Long productId;
    @BeforeEach void setup() {
        reservations.deleteAll();
        products.deleteAll();
        Product p = new Product();
        p.setSku("INVENTORY-TEST"); p.setName("Test product"); p.setPrice(new BigDecimal("10.00"));
        p.setStockQuantity(5); p.setActive(true);
        productId = products.saveAndFlush(p).getId();
    }
    @Test void reserveAndReleaseAreIdempotentAndKeepOriginalPrices() {
        String id = UUID.randomUUID().toString();
        var request = new InventoryService.Request(1L, Map.of(productId, 2));
        var first = inventory.reserve(id, request);
        Product product = products.findById(productId).orElseThrow();
        product.setPrice(new BigDecimal("15.00"));
        products.saveAndFlush(product);
        var retry = inventory.reserve(id, request);
        assertThat(retry.items().getFirst().price).isEqualByComparingTo("10.00");
        assertThat(products.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThatThrownBy(() -> inventory.reserve(id, new InventoryService.Request(1L, Map.of(productId, 3))))
                .isInstanceOf(ResponseStatusException.class);
        inventory.release(id); inventory.release(id);
        assertThat(products.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(5);
    }
    @Test void invalidMultiItemReservationRollsBackAllStock() {
        String id = UUID.randomUUID().toString();
        assertThatThrownBy(() -> inventory.reserve(id, new InventoryService.Request(1L, Map.of(productId, 2, Long.MAX_VALUE, 1))))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(products.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(reservations.existsById(id)).isFalse();
    }
    @Test void concurrentCustomersCannotOversell() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> buy = () -> {
                start.await();
                try { inventory.reserve(UUID.randomUUID().toString(), new InventoryService.Request(1L, Map.of(productId, 4))); return true; }
                catch (ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(422); return false; }
            };
            Future<Boolean> first = pool.submit(buy), second = pool.submit(buy);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(products.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(1);
    }
    @Test void reservedProductCannotBeDeletedUntilStockIsReleased() {
        String id = UUID.randomUUID().toString();
        inventory.reserve(id, new InventoryService.Request(1L, Map.of(productId, 1)));
        assertThatThrownBy(() -> productService.deleteProduct(productId)).isInstanceOf(DuplicateResourceException.class);
        inventory.release(id);
        productService.deleteProduct(productId);
        assertThat(products.existsById(productId)).isFalse();
    }
    @Test void inventoryApiRequiresServiceAuthentication() throws Exception {
        String path = "/internal/inventory/reservations/" + UUID.randomUUID();
        mvc.perform(delete(path)).andExpect(status().isUnauthorized());
        mvc.perform(delete(path).header("X-Service-Key", "wrong")).andExpect(status().isUnauthorized());
    }

    @Test void releasedUnknownReservationCannotArriveLater() {
        String id = UUID.randomUUID().toString(); inventory.release(id);
        assertThatThrownBy(() -> inventory.reserve(id, new InventoryService.Request(1L, Map.of(productId, 1))))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(products.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(5);
    }
    @Test void committedStockCannotBeReleasedAndCommitIsIdempotent() {
        String id = UUID.randomUUID().toString();
        inventory.reserve(id, new InventoryService.Request(1L, Map.of(productId, 2)));
        inventory.commit(id); inventory.commit(id);
        assertThatThrownBy(() -> inventory.release(id)).isInstanceOf(ResponseStatusException.class);
        assertThat(products.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(3);
    }
}
