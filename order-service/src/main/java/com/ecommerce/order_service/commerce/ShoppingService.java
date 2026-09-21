package com.ecommerce.order_service.commerce;

import com.ecommerce.order_service.client.ServiceClients;
import com.ecommerce.order_service.model.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShoppingService {
    private final ReviewRepository reviews;
    private final WishlistRepository wishlist;
    private final OrderRepository orders;
    private final ServiceClients clients;
    private final ObjectMapper json;
    public ShoppingService(ReviewRepository reviews, WishlistRepository wishlist, OrderRepository orders, ServiceClients clients, ObjectMapper json) {
        this.reviews = reviews; this.wishlist = wishlist; this.orders = orders; this.clients = clients; this.json = json;
    }
    public record ReviewRequest(@NotNull @Positive Long productId, @Min(1) @Max(5) int rating, @NotBlank @Size(max = 2000) String comment) {}
    public record ReviewView(Long id, int rating, String comment, Instant createdAt, boolean verifiedPurchase) {}
    public record Reviews(double averageRating, long reviewCount, Page<ReviewView> reviews) {}
    @Transactional(readOnly = true)
    public Reviews reviews(Long productId, int page, int size) {
        Double average = reviews.average(productId);
        return new Reviews(average == null ? 0 : average, reviews.countByProductId(productId),
                reviews.findByProductIdOrderByCreatedAtDesc(productId, PageRequest.of(page, size)).map(this::view));
    }
    @Transactional
    public ReviewView review(Long userId, String orderId, ReviewRequest request) {
        var order = orders.lock(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.userId.equals(userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        if (order.deliveredAt == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only delivered purchases can be reviewed");
        boolean purchased = false;
        try { for (var item : json.readTree(order.itemsJson)) if (item.path("productId").asLong() == request.productId()) purchased = true; }
        catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
        if (!purchased) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product is not part of this order");
        if (reviews.existsByUserIdAndProductId(userId, request.productId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this product");
        Review r = new Review(); r.userId = userId; r.productId = request.productId(); r.orderId = orderId;
        r.rating = request.rating(); r.comment = request.comment().trim();
        return view(reviews.saveAndFlush(r));
    }
    @Transactional(readOnly = true)
    public Page<WishlistEntry> wishlist(Long userId, int page, int size) { return wishlist.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)); }
    @Transactional
    public WishlistEntry save(Long userId, Long productId) {
        var existing = wishlist.findByUserIdAndProductId(userId, productId);
        if (existing.isPresent()) return existing.get();
        clients.requireProduct(productId);
        WishlistEntry entry = new WishlistEntry(); entry.userId = userId; entry.productId = productId;
        return wishlist.saveAndFlush(entry);
    }
    @Transactional
    public void remove(Long userId, Long productId) { wishlist.deleteByUserIdAndProductId(userId, productId); }
    private ReviewView view(Review r) { return new ReviewView(r.id, r.rating, r.comment, r.createdAt, true); }
}
