package com.ecommerce.product_service.inventory;

import com.ecommerce.product_service.repository.ProductRepository;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {
    private final ProductRepository products;
    private final ReservationRepository reservations;
    public InventoryService(ProductRepository products, ReservationRepository reservations) {
        this.products = products; this.reservations = reservations;
    }
    public record Request(@NotNull @Positive Long userId,
            @NotEmpty @Size(max = 100) Map<@NotNull @Positive Long, @NotNull @Positive Integer> items) {}
    public record Result(String id, String state, List<ReservationLine> items) {}

    @Transactional
    public Result reserve(String id, Request request) {
        var existing = reservations.lock(id);
        if (existing.isPresent()) {
            StockReservation r = existing.get();
            Map<Long, Integer> saved = new TreeMap<>();
            r.items.forEach(i -> saved.put(i.productId, i.quantity));
            if (!r.userId.equals(request.userId()) || !saved.equals(request.items()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation request differs from the original");
            if (!r.state.equals("RESERVED"))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation has been released");
            return result(r);
        }
        StockReservation r = reservations.saveAndFlush(new StockReservation(id, request.userId()));
        for (var entry : new TreeMap<>(request.items()).entrySet()) {
            var product = products.findLocked(entry.getKey()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A product no longer exists"));
            if (!product.isActive() || product.getStockQuantity() < entry.getValue())
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A product is inactive or has insufficient stock");
            r.items.add(new ReservationLine(product.getId(), product.getName(), product.getPrice(), entry.getValue()));
            product.setStockQuantity(product.getStockQuantity() - entry.getValue());
        }
        products.flush();
        reservations.flush();
        return result(r);
    }

    @Transactional
    public void release(String id) {
        StockReservation r = reservations.lock(id).orElseGet(() -> reservations.saveAndFlush(new StockReservation(id, 0L)));
        if (r.state.equals("RELEASED")) return;
        if (!r.state.equals("RESERVED")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Shipped or returned stock cannot be released");
        for (var item : r.items.stream().sorted(Comparator.comparing(i -> i.productId)).toList()) {
            var product = products.findLocked(item.productId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.CONFLICT, "Reserved product is missing"));
            long restored = (long) product.getStockQuantity() + item.quantity;
            if (restored > Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock exceeds supported quantity");
            product.setStockQuantity((int) restored);
        }
        r.state = "RELEASED";
    }
    @Transactional
    public void commit(String id) {
        StockReservation r = reservations.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
        if (r.state.equals("RELEASED")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation was released");
        if (!r.state.startsWith("RETURNED")) r.state = "COMMITTED";
    }
    @Transactional
    public void receiveReturn(String id, boolean restock) {
        StockReservation r = reservations.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
        String target = restock ? "RETURNED" : "RETURNED_DISCARDED";
        if (target.equals(r.state)) return;
        if (!Set.of("RESERVED", "COMMITTED").contains(r.state)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock has already been released or returned with a different decision");
        if (restock) for (var item : r.items.stream().sorted(Comparator.comparing(i -> i.productId)).toList()) {
            var p = products.findLocked(item.productId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Product must exist to restock a return"));
            long quantity = (long) p.getStockQuantity() + item.quantity;
            if (quantity > Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock exceeds supported quantity");
            p.setStockQuantity((int) quantity);
        }
        r.state = target;
    }
    private Result result(StockReservation r) { return new Result(r.id, r.state, List.copyOf(r.items)); }
}
