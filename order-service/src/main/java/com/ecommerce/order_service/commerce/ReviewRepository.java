package com.ecommerce.order_service.commerce;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable page);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    long countByProductId(Long productId);
    @Query("select avg(r.rating) from Review r where r.productId = :id")
    Double average(@Param("id") Long productId);
}
