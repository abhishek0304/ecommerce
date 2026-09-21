package com.ecommerce.order_service.client;

import com.ecommerce.order_service.api.OrderDtos.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import feign.FeignException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ServiceClients {
    private final CartApi cart;
    private final InventoryApi inventory;
    private final UserApi users;
    private final String key;
    public ServiceClients(CartApi cart, InventoryApi inventory, UserApi users,
                          @Value("${internal.service-key}") String key) {
        if (key.isBlank()) throw new IllegalArgumentException("Internal service key cannot be blank");
        this.cart = cart; this.inventory = inventory; this.users = users; this.key = key;
    }
    public CartSnapshot cart(Long userId) {
        return cart.get(userId, key);
    }
    public JsonNode address(Long id, String bearer) {
        JsonNode addresses = users.addresses(bearer);
        if (addresses == null || !addresses.isArray()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service returned an invalid address list");
        for (JsonNode address : addresses) {
            if (address.path("id").asLong(-1) == id) return address;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address does not belong to this user");
    }
    public Reservation reserve(String orderId, Long userId, Map<Long, Integer> items) {
        return inventory.reserve(orderId, key, Map.of("userId", userId, "items", items));
    }
    public void release(String id) {
        inventory.release(id, key);
    }
    public void commitInventory(String id) {
        inventory.commit(id, key);
    }
    public void returnInventory(String id, boolean restock) {
        inventory.returnInventory(id, key, Map.of("restock", restock));
    }
    public void requireProduct(Long id) {
        try {
            JsonNode product = inventory.product(id);
            if (product == null || !product.path("active").asBoolean()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        } catch (FeignException ex) {
            if (ex.status() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            throw ex;
        }
    }
    public boolean consume(Long userId, Long version) {
        JsonNode result = cart.consume(userId, key, Map.of("version", version));
        if (result == null || !result.has("cleared")) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid cart response");
        return result.path("cleared").asBoolean();
    }
}
