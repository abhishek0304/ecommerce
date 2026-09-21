package com.ecommerce.order_service.client;

import feign.Request;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

// Intentionally not component-scanned: applies only to the external provider client.
public class ProviderFeignConfiguration {
    @Bean public Request.Options feignRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 8, TimeUnit.SECONDS, false);
    }
}
