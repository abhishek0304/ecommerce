package com.ecommerce.order_service.shipping;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ShipmentRepository extends JpaRepository<Shipment,String> {
    List<Shipment> findByOrderIdOrderByCreatedAtAsc(String orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from Shipment s where s.id=:id")
    Optional<Shipment> lock(@Param("id") String id);
    @Query("select s.id from Shipment s where s.status='BOOKED' and s.mode<>:excludedMode and s.nextTrackAt<=:now order by s.nextTrackAt")
    List<String> due(@Param("now") java.time.Instant now,@Param("excludedMode") ShippingDtos.Mode excludedMode,org.springframework.data.domain.Pageable page);
}
