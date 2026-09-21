package com.ecommerce.order_service.events;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.*;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.handler.*;
import io.micrometer.observation.*;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static org.assertj.core.api.Assertions.*;

class OutboxTracingTests {
    final SdkTracerProvider provider = SdkTracerProvider.builder().build();
    final io.opentelemetry.api.trace.Tracer otel = provider.get("outbox-test");
    final Tracer tracer = new OtelTracer(otel, new OtelCurrentTraceContext(), event -> {});
    final Propagator propagator = new OtelPropagator(
            ContextPropagators.create(W3CTraceContextPropagator.getInstance()), otel);
    final ObservationRegistry observations = ObservationRegistry.create();
    final OutboxTracing tracing = tracing();

    private OutboxTracing tracing() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("tracer", tracer);
        beans.addBean("propagator", propagator);
        observations.observationConfig().observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler(
                new PropagatingReceiverTracingObservationHandler<>(tracer, propagator),
                new DefaultTracingObservationHandler(tracer)));
        return new OutboxTracing(beans.getBeanProvider(Tracer.class), beans.getBeanProvider(Propagator.class), observations);
    }
    @AfterEach void close() { provider.close(); }

    @Test void resumesPersistedTraceOnAnotherThreadAndOnRetry() throws Exception {
        var parent = tracer.nextSpan().name("checkout").start();
        var event = new OutboxEvent();
        try (var scope = tracer.withSpan(parent)) { tracing.capture(event); }
        parent.end();
        assertThat(event.traceParent).contains(parent.context().traceId());
        // Use a fresh row-shaped object: no live span or thread-local context survives persistence.
        var loaded = new OutboxEvent();
        loaded.traceParent = event.traceParent;
        loaded.traceState = event.traceState;
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                assertThatThrownBy(() -> tracing.publish(loaded, () -> {
                    assertThat(tracer.currentSpan().context().traceId()).isEqualTo(parent.context().traceId());
                    throw new IllegalStateException("broker unavailable");
                })).isInstanceOf(IllegalStateException.class);
                assertThat(tracer.currentSpan()).isNull();
                Observation.createNotStarted("scheduler", observations).observe(() ->
                    tracing.publish(loaded, () -> {
                        assertThat(tracer.currentSpan().context().traceId()).isEqualTo(parent.context().traceId());
                        assertThat(tracer.currentSpan().context().spanId()).isNotEqualTo(parent.context().spanId());
                        Observation.createNotStarted("producer", observations).observe(() ->
                            assertThat(tracer.currentSpan().context().traceId()).isEqualTo(parent.context().traceId()));
                    }));
                assertThat(tracer.currentSpan()).isNull();
            }).get();
        }
    }

    @Test void legacyRowsStartANewTraceAndRestoreCallingScope() {
        var caller = tracer.nextSpan().name("scheduler").start();
        try (var scope = tracer.withSpan(caller)) {
            tracing.publish(new OutboxEvent(), () -> assertThat(tracer.currentSpan()).isNotNull());
            assertThat(tracer.currentSpan().context().spanId()).isEqualTo(caller.context().spanId());
        } finally { caller.end(); }
    }

    @Test void disabledTracingStillPublishes() {
        var beans = new StaticListableBeanFactory();
        var disabled = new OutboxTracing(beans.getBeanProvider(Tracer.class), beans.getBeanProvider(Propagator.class), ObservationRegistry.NOOP);
        var event = new OutboxEvent();
        disabled.capture(event);
        assertThat(event.traceParent).isNull();
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        disabled.publish(event, () -> called.set(true));
        assertThat(called).isTrue();
    }
}
