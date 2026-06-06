package com.rewabank.fraud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
	"spring.flyway.enabled=false",
	"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
	"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/auth/realms/test/protocol/openid-connect/certs",
	"build.version=1.0.0"
})
class FraudApplicationTests {

	@MockBean
	RedisConnectionFactory redisConnectionFactory;

	@MockBean
	@SuppressWarnings("rawtypes")
	KafkaTemplate kafkaTemplate;

	@Test
	void contextLoads() {
		// Test that the application context loads successfully
		assert true;
	}

}
