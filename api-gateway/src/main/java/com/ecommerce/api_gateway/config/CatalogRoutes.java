package com.ecommerce.api_gateway.config;
import org.springframework.context.annotation.*;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.beans.factory.annotation.Value;
@Configuration
public class CatalogRoutes {
    @Bean RouteLocator catalogRouteLocator(RouteLocatorBuilder builder, @Value("${product-service.uri:lb://product-service}") String products, @Value("${user-service.uri:lb://user-service}") String users) {
        return builder.routes()
            .route("products", r -> r.path("/api/v1/products", "/api/v1/products/**").uri(products))
            .route("users", r -> r.path("/api/auth/**", "/api/users/**", "/api/addresses", "/api/addresses/**").uri(users))
            .build();
    }
}

