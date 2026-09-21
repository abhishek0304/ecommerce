package com.ecommerce.cart_service.repository;
import com.ecommerce.cart_service.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CartRepository extends JpaRepository<Cart, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Cart c where c.userId = :id")
    java.util.Optional<Cart> findLocked(@org.springframework.data.repository.query.Param("id") Long id);
}
