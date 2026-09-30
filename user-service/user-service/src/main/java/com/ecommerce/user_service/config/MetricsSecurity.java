package com.ecommerce.user_service.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class MetricsSecurity {
 @Bean @Order(0) SecurityFilterChain metricsChain(HttpSecurity http, Environment env) throws Exception {
  int port=env.getProperty("management.server.port",Integer.class,-1);
  return http.securityMatcher(r -> port>0 && r.getLocalPort()==port && r.getMethod().equals("GET") && r.getRequestURI().equals("/actuator/prometheus"))
   .authorizeHttpRequests(a->a.anyRequest().permitAll()).build();
 }
}
