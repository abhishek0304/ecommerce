package com.ecommerce.order_service;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.ecommerce.order_service.config.OperationsMetrics;
import static org.junit.jupiter.api.Assertions.*;
class OperationsMetricsTests {
 @Test void staleWorkAppearsAndResolvesWithoutCountingFreshOrCompletedRows(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:metrics_"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  Flyway.configure().dataSource(ds).load().migrate();
  var jdbc=new JdbcTemplate(ds);jdbc.execute("INSERT INTO order_event_outbox(event_id,order_id,payload,created_at) VALUES ('old','o','{}',TIMESTAMP '2020-01-01 00:00:00'),('fresh','o','{}',CURRENT_TIMESTAMP)");
  var meters=new SimpleMeterRegistry();
  try {
   var metrics=new OperationsMetrics(jdbc,meters);metrics.refresh();
   assertEquals(1.0,meters.get("ecommerce.operations.stale").tag("queue","outbox").gauge().value());
   jdbc.execute("UPDATE order_event_outbox SET published_at=CURRENT_TIMESTAMP");metrics.refresh();
   assertEquals(0.0,meters.get("ecommerce.operations.stale").tag("queue","outbox").gauge().value());
  } finally { meters.close(); }
 }
}
