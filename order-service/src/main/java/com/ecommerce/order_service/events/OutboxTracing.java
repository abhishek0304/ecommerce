package com.ecommerce.order_service.events;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Carries only W3C trace context across the durable outbox boundary, never credentials. */
@Component
public class OutboxTracing {
    private final ObjectProvider<Tracer> tracers;
    private final ObjectProvider<Propagator> propagators;
    private final ObservationRegistry observations;

    public OutboxTracing(ObjectProvider<Tracer> tracers, ObjectProvider<Propagator> propagators,
                         ObservationRegistry observations) {
        this.tracers = tracers;
        this.propagators = propagators;
        this.observations = observations;
    }

    public void capture(OutboxEvent event) {
        var tracer = tracers.getIfAvailable();
        var propagator = propagators.getIfAvailable();
        var span = tracer == null ? null : tracer.currentSpan();
        if (span == null || propagator == null) return;
        Map<String, String> headers = new HashMap<>();
        propagator.inject(span.context(), headers, Map::put);
        event.traceParent = headers.get("traceparent");
        event.traceState = headers.get("tracestate");
    }

    public void publish(OutboxEvent event, Runnable send) {
        var tracer = tracers.getIfAvailable();
        var propagator = propagators.getIfAvailable();
        if (tracer == null || propagator == null) {
            send.run();
            return;
        }
        Map<String, String> headers = new HashMap<>();
        if (event.traceParent != null) headers.put("traceparent", event.traceParent);
        if (event.traceState != null) headers.put("tracestate", event.traceState);
        var context = new ReceiverContext<Map<String, String>>(Map::get);
        context.setCarrier(headers);
        // A receiver observation extracts the persisted parent and becomes the parent of
        // Kafka's producer observation, even when a scheduler observation is active.
        Observation.createNotStarted("order.outbox.publish", () -> context, observations)
                .observe(send);
    }
}
