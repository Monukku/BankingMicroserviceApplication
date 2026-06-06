package com.rewabank.auth;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.datasource.url=jdbc:h2:mem:authtestdb;MODE=PostgreSQL",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
	"spring.flyway.enabled=false",
	"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
	"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/auth/realms/test/protocol/openid-connect/certs",
	"keycloak.server-url=http://localhost:8080",
	"keycloak.realm=rewabank",
	"keycloak.client-id=auth-service",
	"keycloak.client-secret=secret",
	"build.version=1.0.0"
})
class AuthApplicationTests {

	@MockBean RedisConnectionFactory redisConnectionFactory;
	@MockBean StringRedisTemplate stringRedisTemplate;
	@MockBean @SuppressWarnings("rawtypes") KafkaTemplate kafkaTemplate;
	@MockBean Keycloak keycloak;

	@Test
	void contextLoads() {
	}

}
