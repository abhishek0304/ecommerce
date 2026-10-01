package com.ecommerce.product_service.inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
public interface MovementRepository extends JpaRepository<StockMovement,Long> {
    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(Long id, Pageable pageable);
}
