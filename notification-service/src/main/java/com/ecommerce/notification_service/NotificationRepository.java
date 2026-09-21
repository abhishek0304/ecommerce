package com.ecommerce.notification_service;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationRepository extends JpaRepository<Notification, String> {
    Page<Notification> findByUserIdOrderByReceivedAtDesc(Long userId, Pageable pageable);
}
