package com.ecommerce.order_service.config;
import io.micrometer.core.instrument.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.sql.Timestamp;
import java.time.Instant;
@Component
public class OperationsMetrics {
 private final JdbcTemplate jdbc;
 private final Map<String,AtomicLong> values=new HashMap<>();
 private final AtomicLong lastSuccess=new AtomicLong(Instant.now().getEpochSecond());
 public OperationsMetrics(JdbcTemplate jdbc, MeterRegistry registry){this.jdbc=jdbc;
    register(registry,"outbox");
    register(registry,"recovery");
    register(registry,"shipping");
  Gauge.builder("ecommerce.operations.collection.age.seconds",lastSuccess,v->Instant.now().getEpochSecond()-v.get()).register(registry);
 }
 private void register(MeterRegistry registry,String queue){var value=new AtomicLong();values.put(queue,value);Gauge.builder("ecommerce.operations.stale",value,AtomicLong::get).tag("queue",queue).register(registry);}
 @Scheduled(fixedDelayString="${operations.metrics.delay-ms:30000}",initialDelayString="${operations.metrics.delay-ms:30000}")
 public void refresh(){
  var cutoff=Timestamp.from(Instant.now().minusSeconds(900));
    values.get("outbox").set(jdbc.queryForObject("select count(*) from order_event_outbox where published_at is null and created_at < ?",Long.class,cutoff));
    values.get("recovery").set(jdbc.queryForObject("select count(*) from purchase_orders where status in ('CREATING','RESERVED','CANCELLING','RETURN_RECEIVING') and updated_at < ?",Long.class,cutoff));
    values.get("shipping").set(jdbc.queryForObject("select count(*) from order_shipments where status in ('UNKNOWN','BOOKING','CREATED') and created_at < ?",Long.class,cutoff));
  lastSuccess.set(Instant.now().getEpochSecond());
 }
}
