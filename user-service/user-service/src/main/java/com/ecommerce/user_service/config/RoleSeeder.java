package com.ecommerce.user_service.config;
import com.ecommerce.user_service.entity.Role; import com.ecommerce.user_service.entity.RoleName; import com.ecommerce.user_service.repository.RoleRepository; import org.springframework.boot.*; import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration;
@Configuration public class RoleSeeder { @Bean CommandLineRunner seedRoles(RoleRepository repo){return a->{for(RoleName n:RoleName.values()) if(repo.findByName(n).isEmpty())repo.save(new Role(n));};} }
