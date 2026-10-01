package com.ecommerce.order_service.commerce;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DeliveryRepository extends JpaRepository<DeliveryRule,String> {}
