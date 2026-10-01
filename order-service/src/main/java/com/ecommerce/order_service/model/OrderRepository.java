package com.ecommerce.order_service.model;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface OrderRepository extends JpaRepository<PurchaseOrder, String> {
    Page<PurchaseOrder> findByCreatedAtGreaterThanEqual(java.time.Instant since, Pageable pageable);
    Optional<PurchaseOrder> findByUserIdAndKeyHash(Long userId, String keyHash);
    Optional<PurchaseOrder> findByRazorpayOrderId(String razorpayOrderId);
    Optional<PurchaseOrder> findByRazorpayPaymentId(String razorpayPaymentId);
    Page<PurchaseOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> lock(@Param("id") String id);
    @Query("select o.id from PurchaseOrder o where o.status in ('CREATING', 'RESERVED', 'PENDING_PAYMENT', 'CANCELLING', 'RETURN_RECEIVING') or (o.status in ('CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED') and o.cartCleanupDone = false) or (o.status in ('SHIPPED', 'DELIVERED') and o.inventoryCommitted = false) or (o.status in ('EXPIRED', 'CANCELLED', 'RETURNED') and o.paymentMethod = 'RAZORPAY' and o.paymentStatus <> 'REFUNDED') or (o.status in ('EXPIRED', 'CANCELLED', 'FAILED') and o.couponRedeemed = true) order by o.updatedAt")
    List<String> recoverable(Pageable pageable);
}
