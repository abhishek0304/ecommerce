package com.ecommerce.cart_service.client;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import feign.FeignException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProductClient {
    private final ProductApi client;
    public ProductClient(ProductApi client) { this.client = client; }

    public Product get(Long id) {
        try {
            Product product = client.get(id);
            if (product == null || !id.equals(product.id()) || product.price() == null || product.price().signum() < 0
                    || product.stockQuantity() == null || product.name() == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid product service response");
            }
            return product;
        } catch (FeignException ex) {
            if (ex.status() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service is unavailable");
        }
    }
    public record Product(Long id, String name, BigDecimal price, Integer stockQuantity, boolean active) {}
}
