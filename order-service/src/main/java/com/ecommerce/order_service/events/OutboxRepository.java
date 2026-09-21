package com.ecommerce.order_service.events;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.id")
    List<OutboxEvent> pending(Pageable pageable);
}
