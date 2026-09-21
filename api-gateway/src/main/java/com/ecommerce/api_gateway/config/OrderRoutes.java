package com.ecommerce.api_gateway.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;
@Configuration
public class OrderRoutes {
    @Bean
    RouteLocator orderRouteLocator(RouteLocatorBuilder builder,
            @Value("${order-service.uri:lb://order-service}") String uri) {
        return builder.routes().route("order-api", r ->
                r.path("/api/orders", "/api/orders/**", "/api/admin/orders", "/api/admin/orders/**", "/api/payments/razorpay/webhook",
                        "/api/reviews/**", "/api/wishlist", "/api/wishlist/**", "/api/coupons/**", "/api/admin/coupons", "/api/admin/coupons/**").uri(uri)).build();
    }
}
