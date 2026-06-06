package com.rewabank.customers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import io.minio.MinioClient;

@SpringBootTest
        @EnableAutoConfiguration(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        KafkaAutoConfiguration.class
})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "build.version=1.0.0",
        "minio.bucket-name=test-bucket",
        "minio.url=http://localhost:9000",
        "minio.access-key=test",
        "minio.secret-key=test",
        "encryption.aes-key=dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleXRlc3Q=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/realms/test/protocol/openid-connect/certs"
})
class CustomerApplicationTests {

    @MockBean RedisConnectionFactory redisConnectionFactory;
    @MockBean KafkaTemplate<?, ?> kafkaTemplate;
    @MockBean MinioClient minioClient;

    @Test
    void contextLoads() {
    }
}