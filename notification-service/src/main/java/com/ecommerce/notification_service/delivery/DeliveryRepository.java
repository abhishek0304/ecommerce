package com.ecommerce.notification_service.delivery;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface DeliveryRepository extends JpaRepository<Delivery, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> lock(String id);
    @Query("select d.id from Delivery d where d.status in ('PENDING','SENDING') and d.nextAttemptAt <= :now order by d.nextAttemptAt")
    List<String> due(Instant now, Pageable page);
    Page<Delivery> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable page);
}
