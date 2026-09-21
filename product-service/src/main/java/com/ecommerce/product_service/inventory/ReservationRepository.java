package com.ecommerce.product_service.inventory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<StockReservation, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReservation r where r.id = :id")
    Optional<StockReservation> lock(@Param("id") String id);
    @Query("select count(r) > 0 from StockReservation r join r.items i where r.state in ('RESERVED', 'COMMITTED') and i.productId = :productId")
    boolean hasReservation(@Param("productId") Long productId);
}
