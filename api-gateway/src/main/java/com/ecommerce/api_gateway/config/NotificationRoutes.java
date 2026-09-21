package com.ecommerce.api_gateway.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;
@Configuration
public class NotificationRoutes {
    @Bean RouteLocator notificationRouteLocator(RouteLocatorBuilder builder,
            @Value("${notification-service.uri:lb://notification-service}") String uri) {
        return builder.routes().route("notification-api", r -> r.path("/api/notifications", "/api/notifications/**").uri(uri)).build();
    }
}
