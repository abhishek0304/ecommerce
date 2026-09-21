package com.ecommerce.product_service;

import com.ecommerce.product_service.dto.ProductRequest;
import com.ecommerce.product_service.exception.DuplicateResourceException;
import com.ecommerce.product_service.exception.ProductNotFoundException;
import com.ecommerce.product_service.service.ProductService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProductRegressionTests {
    @Autowired ProductService service;

    @Test
    void productLifecycleAndDuplicateSku() {
        String sku = "sku-" + UUID.randomUUID();
        ProductRequest request = new ProductRequest(sku, "Product", "Description", new BigDecimal("12.50"), 5, "test", true);
        var created = service.createProduct(request);
        assertNotNull(created.id());
        assertNotNull(created.createdAt());
        assertEquals(sku.toUpperCase(java.util.Locale.ROOT), created.sku());
        assertEquals(created.id(), service.getProduct(created.id()).id());
        assertThrows(DuplicateResourceException.class, () -> service.createProduct(request));
        var updated = service.updateProduct(created.id(), new ProductRequest(sku, "Changed", null, new BigDecimal("15.00"), 0, null, false));
        assertEquals("Changed", updated.name());
        assertFalse(service.getProduct(created.id()).active());
        service.deleteProduct(created.id());
        assertThrows(ProductNotFoundException.class, () -> service.getProduct(created.id()));
    }
}
