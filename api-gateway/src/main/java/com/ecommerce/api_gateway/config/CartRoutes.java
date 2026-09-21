package com.ecommerce.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CartRoutes {
    @Bean
    RouteLocator cartRouteLocator(RouteLocatorBuilder builder,
            @Value("${cart-service.uri:lb://cart-service}") String cartServiceUri) {
        return builder.routes()
                .route("cart-api", route -> route.path("/api/cart", "/api/cart/**").uri(cartServiceUri))
                .build();
    }
}
