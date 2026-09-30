package com.ecommerce.user_service.config;
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
    register(registry,"account_message");
  Gauge.builder("ecommerce.operations.collection.age.seconds",lastSuccess,v->Instant.now().getEpochSecond()-v.get()).register(registry);
 }
 private void register(MeterRegistry registry,String queue){var value=new AtomicLong();values.put(queue,value);Gauge.builder("ecommerce.operations.stale",value,AtomicLong::get).tag("queue",queue).register(registry);}
 @Scheduled(fixedDelayString="${operations.metrics.delay-ms:30000}",initialDelayString="${operations.metrics.delay-ms:30000}")
 public void refresh(){
  var cutoff=Timestamp.from(Instant.now().minusSeconds(900));
    values.get("account_message").set(jdbc.queryForObject("select count(*) from account_message_outbox where status = 'PENDING' and next_attempt_at < ?",Long.class,cutoff));
  lastSuccess.set(Instant.now().getEpochSecond());
 }
}
