package com.ecommerce.user_service.repository;
import com.ecommerce.user_service.entity.LoginHistory; import org.springframework.data.jpa.repository.JpaRepository;
public interface LoginHistoryRepository extends JpaRepository<LoginHistory,Long>{}
