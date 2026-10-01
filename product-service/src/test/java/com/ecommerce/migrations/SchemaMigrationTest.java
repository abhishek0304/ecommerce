package com.ecommerce.migrations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.Configuration;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import jakarta.persistence.Entity;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class SchemaMigrationTest {
 @Test void unmanagedDatabaseIsNotSilentlyBaselined() throws Exception {
  String url="jdbc:h2:mem:legacy_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1";
  try(var c=DriverManager.getConnection(url,"sa","");var stmt=c.createStatement()){stmt.execute("CREATE TABLE existing_customer(id BIGINT PRIMARY KEY)");}
  var flyway=Flyway.configure().dataSource(url,"sa","").baselineOnMigrate(false).load();
  assertThrows(org.flywaydb.core.api.FlywayException.class,flyway::migrate);
 }
 @Test void h2MigrationAndValidation() throws Exception {
  verify("jdbc:h2:mem:migration_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
 }
 @Test @EnabledIfEnvironmentVariable(named="MIGRATION_MYSQL_URL",matches=".+")
 void mysqlMigrationAndValidation() throws Exception {
  String base=System.getenv("MIGRATION_MYSQL_URL"), user=System.getenv("MYSQL_USER"), pass=System.getenv("MYSQL_PWD");
  assertTrue(base.matches("jdbc:mysql://[^/]+/"),"Use server URL with trailing slash, without database or query");
  String db="ec_verify_product_"+UUID.randomUUID().toString().replace("-", "");
  try(var conn=DriverManager.getConnection(base,user,pass);var stmt=conn.createStatement()) {
   stmt.execute("CREATE DATABASE `"+db+"` CHARACTER SET utf8mb4");
   try { verify(base+db,user,pass); } finally { stmt.execute("DROP DATABASE `"+db+"`"); }
  }
 }
 void verify(String url,String user,String pass) throws Exception {
  var flyway=Flyway.configure().dataSource(url,user,pass).cleanDisabled(true).baselineOnMigrate(false).load();
  assertEquals(3,flyway.migrate().migrationsExecuted);
  try(var c=DriverManager.getConnection(url,user,pass);var s=c.createStatement()) {
   s.execute("CREATE TABLE migration_probe (id BIGINT PRIMARY KEY, value_text VARCHAR(100))");
   s.execute("INSERT INTO migration_probe VALUES (1, 'retained')");
  }
  assertEquals(0,flyway.migrate().migrationsExecuted);
  var cfg=new Configuration();
  cfg.setPhysicalNamingStrategy(new org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy());
  cfg.setImplicitNamingStrategy(new org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy());
  cfg.setProperty("hibernate.connection.url",url);cfg.setProperty("hibernate.connection.username",user);cfg.setProperty("hibernate.connection.password",pass);
  cfg.setProperty("hibernate.hbm2ddl.auto","validate");
  var scan=new ClassPathScanningCandidateComponentProvider(false);scan.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
  for(var bean:scan.findCandidateComponents("com.ecommerce"))cfg.addAnnotatedClass(Class.forName(bean.getBeanClassName()));
  // H2 maps MySQL LONGTEXT to VARCHAR; validate types against real MySQL only.
  if(url.startsWith("jdbc:mysql:")) {try(var factory=cfg.buildSessionFactory()){}}
  assertThrows(org.flywaydb.core.api.FlywayException.class,flyway::clean);
  try(var c=DriverManager.getConnection(url,user,pass);var s=c.createStatement()) {
   try(var rows=s.executeQuery("SELECT value_text FROM migration_probe WHERE id=1")){assertTrue(rows.next());assertEquals("retained",rows.getString(1));}
   String q=url.startsWith("jdbc:h2:") ? "\"" : "`";
   s.execute("UPDATE "+q+"flyway_schema_history"+q+" SET "+q+"checksum"+q+"=0 WHERE "+q+"version"+q+"='2'");
  }
  assertThrows(org.flywaydb.core.api.FlywayException.class,flyway::validate);
 }
}
