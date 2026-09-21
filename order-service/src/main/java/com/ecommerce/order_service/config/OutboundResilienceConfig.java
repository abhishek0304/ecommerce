package com.ecommerce.order_service.config;

import feign.Capability;
import feign.Client;
import io.github.resilience4j.circuitbreaker.*;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.circuit-breaker.enabled", havingValue = "true", matchIfMissing = true)
public class OutboundResilienceConfig {
    @Bean public Capability outboundCircuitBreakers(CircuitBreakerRegistry registry) {
        return new CircuitBreakerCapability(registry);
    }
    public static class CircuitBreakerCapability implements Capability {
        private final CircuitBreakerRegistry registry;
        public CircuitBreakerCapability(CircuitBreakerRegistry registry) { this.registry = registry; }
        @Override public Client enrich(Client delegate) {
            var config = CircuitBreakerConfig.custom().slidingWindowSize(20).minimumNumberOfCalls(10)
                    .failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(30))
                    .permittedNumberOfCallsInHalfOpenState(3).build();
            return (request, options) -> {
                var circuit = registry.circuitBreaker(URI.create(request.url()).getAuthority(), config);
                if (!circuit.tryAcquirePermission()) throw new IOException("Downstream circuit is open");
                long started = System.nanoTime();
                try {
                    var response = delegate.execute(request, options);
                    if (response.status() >= 500 || response.status() == 429)
                        circuit.onError(System.nanoTime() - started, TimeUnit.NANOSECONDS, new IOException("Downstream unavailable"));
                    else circuit.onSuccess(System.nanoTime() - started, TimeUnit.NANOSECONDS);
                    return response;
                } catch (IOException | RuntimeException ex) {
                    circuit.onError(System.nanoTime() - started, TimeUnit.NANOSECONDS, ex);
                    throw ex;
                }
            };
        }
    }
}