package com.ecommerce.notification_service;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.ecommerce.notification_service.config.OperationsMetrics;
import static org.junit.jupiter.api.Assertions.*;
class OperationsMetricsTests {
 @Test void staleWorkAppearsAndResolvesWithoutCountingFreshOrCompletedRows(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:metrics_"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  Flyway.configure().dataSource(ds).load().migrate();
  var jdbc=new JdbcTemplate(ds);jdbc.execute("INSERT INTO message_deliveries(id,user_id,channel,fingerprint,subject,body,attempts,status,created_at) VALUES ('old',1,'EMAIL','hash','test','test',1,'UNKNOWN',TIMESTAMP '2020-01-01 00:00:00'),('fresh',1,'EMAIL','hash','test','test',0,'PENDING',CURRENT_TIMESTAMP)");
  var meters=new SimpleMeterRegistry();
  try {
   var metrics=new OperationsMetrics(jdbc,meters);metrics.refresh();
   assertEquals(1.0,meters.get("ecommerce.operations.stale").tag("queue","delivery").gauge().value());
   jdbc.execute("UPDATE message_deliveries SET status='ACCEPTED'");metrics.refresh();
   assertEquals(0.0,meters.get("ecommerce.operations.stale").tag("queue","delivery").gauge().value());
  } finally { meters.close(); }
 }
}
