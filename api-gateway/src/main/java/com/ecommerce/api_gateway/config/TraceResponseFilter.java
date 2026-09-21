package com.ecommerce.api_gateway.config;

import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Exposes the server trace, including on error responses, for support and Tempo lookup. */
@Component
public class TraceResponseFilter implements WebFilter {
    private final ObjectProvider<Tracer> tracers;

    public TraceResponseFilter(ObjectProvider<Tracer> tracers) {
        this.tracers = tracers;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            var tracer = tracers.getIfAvailable();
            var span = tracer == null ? null : tracer.currentSpan();
            if (span != null) {
                String traceId = span.context().traceId();
                exchange.getResponse().beforeCommit(() -> {
                    exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
                    return Mono.empty();
                });
            }
            return chain.filter(exchange);
        });
    }
}
