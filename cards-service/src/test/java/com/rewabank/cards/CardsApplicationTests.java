package com.rewabank.cards;

import com.rewabank.cards.client.AccountsFeignClient;
import com.rewabank.cards.client.CustomersFeignClient;
import com.rewabank.cards.client.FraudFeignClient;
import com.rewabank.cards.client.NotificationFeignClient;
import com.rewabank.cards.kafka.FraudEventConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
        KafkaAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class
})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.cloud.openfeign.circuitbreaker.enabled=false",
        "build.version=1.0.0",
        "encryption.aes-key=dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleXRlc3Q="
})
class CardsApplicationTests {

    @MockitoBean AccountsFeignClient     accountsFeignClient;
    @MockitoBean CustomersFeignClient    customersFeignClient;
    @MockitoBean FraudFeignClient        fraudFeignClient;
    @MockitoBean NotificationFeignClient notificationFeignClient;
    @MockitoBean JwtDecoder              jwtDecoder;
    @MockitoBean FraudEventConsumer      fraudEventConsumer;

    @Test
    void contextLoads() {
    }
}