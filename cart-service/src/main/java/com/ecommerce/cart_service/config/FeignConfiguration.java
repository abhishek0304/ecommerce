package com.ecommerce.cart_service.config;

import feign.Request;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfiguration {
    @Bean public Request.Options feignRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, false);
    }
    // Recovery and delivery workers own retries; never repeat an uncertain payment/send here.
    @Bean public Retryer feignRetryer() { return Retryer.NEVER_RETRY; }
}