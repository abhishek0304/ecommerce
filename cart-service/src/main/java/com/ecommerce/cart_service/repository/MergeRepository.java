package com.ecommerce.cart_service.repository;
import com.ecommerce.cart_service.entity.GuestMerge;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MergeRepository extends JpaRepository<GuestMerge,String> {}
