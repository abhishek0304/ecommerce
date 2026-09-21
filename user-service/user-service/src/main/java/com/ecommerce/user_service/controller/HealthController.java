package com.ecommerce.user_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/hello")
    public String hello(){
        return "User Service is running...";
    }
}
