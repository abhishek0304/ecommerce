package com.ecommerce.user_service;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.ecommerce.user_service.config.OperationsMetrics;
import static org.junit.jupiter.api.Assertions.*;
class OperationsMetricsTests {
 @Test void staleWorkAppearsAndResolvesWithoutCountingFreshOrCompletedRows(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:metrics_"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  Flyway.configure().dataSource(ds).load().migrate();
  var jdbc=new JdbcTemplate(ds);jdbc.execute("INSERT INTO account_message_outbox(id,attempts,status,next_attempt_at) VALUES ('old',0,'PENDING',TIMESTAMP '2020-01-01 00:00:00'),('fresh',0,'PENDING',CURRENT_TIMESTAMP),('queued',0,'QUEUED',TIMESTAMP '2020-01-01 00:00:00')");
  var meters=new SimpleMeterRegistry();
  try {
   var metrics=new OperationsMetrics(jdbc,meters);metrics.refresh();
   assertEquals(1.0,meters.get("ecommerce.operations.stale").tag("queue","account_message").gauge().value());
   jdbc.execute("UPDATE account_message_outbox SET status='QUEUED'");metrics.refresh();
   assertEquals(0.0,meters.get("ecommerce.operations.stale").tag("queue","account_message").gauge().value());
  } finally { meters.close(); }
 }
}

