package com.ecommerce.cart_service;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@org.springframework.cloud.openfeign.EnableFeignClients
@SpringBootApplication
public class CartServiceApplication {
    public static void main(String[] args) { SpringApplication.run(CartServiceApplication.class, args); }
}
