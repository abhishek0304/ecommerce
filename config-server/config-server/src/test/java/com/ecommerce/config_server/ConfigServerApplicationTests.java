package com.ecommerce.config_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ConfigServerApplicationTests {

	@org.springframework.beans.factory.annotation.Autowired
	org.springframework.cloud.config.server.environment.EnvironmentRepository repository;

	@Test
	void contextLoads() {
	}

	@Test
	void loadsWorkspaceConfiguration() {
		var environment = repository.findOne("product-service", "default", null);
		org.junit.jupiter.api.Assertions.assertTrue(environment.getPropertySources().stream()
				.anyMatch(source -> "8083".equals(String.valueOf(source.getSource().get("server.port")))));
	}

}
