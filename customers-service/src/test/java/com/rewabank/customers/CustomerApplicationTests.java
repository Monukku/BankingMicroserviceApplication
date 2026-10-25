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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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
        "s3.endpoint=https://s3.eu-central-003.backblazeb2.com",
        "s3.access-key=test",
        "s3.secret-key=test",
        "s3.bucket-name=test-bucket",
        "s3.region=eu-central-003",
        "encryption.aes-key=dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleXRlc3Q=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/realms/test/protocol/openid-connect/certs"
})
class CustomerApplicationTests {

    @MockBean RedisConnectionFactory redisConnectionFactory;
    @MockBean KafkaTemplate<?, ?> kafkaTemplate;
    @MockBean S3Client s3Client;
    @MockBean S3Presigner s3Presigner;

    @Test
    void contextLoads() {
    }
}