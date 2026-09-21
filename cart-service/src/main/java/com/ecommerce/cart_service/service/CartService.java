package com.ecommerce.cart_service.service;

import com.ecommerce.cart_service.client.ProductClient;
import com.ecommerce.cart_service.dto.CartResponse;
import com.ecommerce.cart_service.entity.Cart;
import com.ecommerce.cart_service.repository.CartRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CartService {
    private final CartRepository carts;
    private final ProductClient products;
    public CartService(CartRepository carts, ProductClient products) { this.carts = carts; this.products = products; }

    @Transactional(readOnly = true)
    public CartResponse get(Long userId) {
        return response(carts.findById(userId).orElseGet(() -> new Cart(userId)), Map.of());
    }

    public CartResponse add(Long userId, Long productId, int quantity) {
        Cart cart = carts.findById(userId).orElseGet(() -> new Cart(userId));
        long total = (long) cart.getItems().getOrDefault(productId, 0) + quantity;
        ProductClient.Product product = validate(productId, total);
        cart.getItems().put(productId, (int) total);
        cart = carts.saveAndFlush(cart);
        return response(cart, Map.of(productId, product));
    }

    public CartResponse update(Long userId, Long productId, int quantity) {
        Cart cart = carts.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));
        if (!cart.getItems().containsKey(productId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
        ProductClient.Product product = validate(productId, quantity);
        cart.getItems().put(productId, quantity);
        carts.flush();
        return response(cart, Map.of(productId, product));
    }

    public void remove(Long userId, Long productId) {
        carts.findById(userId).ifPresent(cart -> cart.getItems().remove(productId));
    }

    public void clear(Long userId) { carts.findById(userId).ifPresent(cart -> cart.getItems().clear()); }

    private ProductClient.Product validate(Long productId, long quantity) {
        if (quantity <= 0 || quantity > Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid quantity");
        ProductClient.Product product = products.get(productId);
        if (!product.active()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is inactive");
        if (quantity > product.stockQuantity()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock");
        return product;
    }

    private CartResponse response(Cart cart, Map<Long, ProductClient.Product> known) {
        var items = new ArrayList<CartResponse.Item>();
        BigDecimal total = BigDecimal.ZERO;
        long quantity = 0;
        for (var entry : cart.getItems().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            ProductClient.Product product;
            try {
                product = known.containsKey(entry.getKey()) ? known.get(entry.getKey()) : products.get(entry.getKey());
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() != 404) throw ex;
                // Retain deleted products for removal; exclude them from pricing.
                items.add(new CartResponse.Item(entry.getKey(), "Product no longer exists", entry.getValue(), null, null, false));
                quantity += entry.getValue();
                continue;
            }
            BigDecimal subtotal = product.price().multiply(BigDecimal.valueOf(entry.getValue()));
            items.add(new CartResponse.Item(product.id(), product.name(), entry.getValue(), product.price(), subtotal,
                    product.active() && product.stockQuantity() >= entry.getValue()));
            total = total.add(subtotal);
            quantity += entry.getValue();
        }
        return new CartResponse(cart.getUserId(), items, quantity, total, cart.getVersion());
    }
}
