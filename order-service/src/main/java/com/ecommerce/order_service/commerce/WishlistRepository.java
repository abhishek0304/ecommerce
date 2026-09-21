package com.ecommerce.order_service.commerce;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WishlistRepository extends JpaRepository<WishlistEntry, Long> {
    Page<WishlistEntry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable page);
    Optional<WishlistEntry> findByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
}
