package com.ecommerce.notification_service;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@org.springframework.cloud.openfeign.EnableFeignClients
@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) { SpringApplication.run(NotificationServiceApplication.class, args); }
}
