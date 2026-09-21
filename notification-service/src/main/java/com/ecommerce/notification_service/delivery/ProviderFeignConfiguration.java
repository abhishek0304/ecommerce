package com.ecommerce.notification_service.delivery;

import feign.Request;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

public class ProviderFeignConfiguration {
    @Bean public Request.Options feignRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 8, TimeUnit.SECONDS, false);
    }
}
