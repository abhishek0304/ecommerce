package com.ecommerce.order_service.support;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface TicketRepository extends JpaRepository<SupportTicket,String> {
    Page<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long id,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from SupportTicket t where t.id=:id")
    Optional<SupportTicket> lock(@org.springframework.data.repository.query.Param("id") String id);
}
