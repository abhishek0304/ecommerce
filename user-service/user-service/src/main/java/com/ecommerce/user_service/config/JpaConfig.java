package com.ecommerce.user_service.config;
import org.springframework.context.annotation.*; import org.springframework.data.domain.AuditorAware; import org.springframework.data.jpa.repository.config.EnableJpaAuditing; import java.util.*;
@Configuration @EnableJpaAuditing public class JpaConfig { @Bean AuditorAware<String> auditor(){ return () -> Optional.ofNullable(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).map(a->a.getName()).or(()->Optional.of("system")); } }
