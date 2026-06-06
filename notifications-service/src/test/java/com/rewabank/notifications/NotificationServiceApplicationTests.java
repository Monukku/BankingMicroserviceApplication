package com.rewabank.notifications;

import com.rewabank.notifications.repository.NotificationLogRepository;
import com.rewabank.notifications.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class,
        KafkaAutoConfiguration.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/test",
        "build.version=1.0.0"
})
class NotificationServiceApplicationTests {

    @MockitoBean NotificationTemplateRepository templateRepository;
    @MockitoBean NotificationLogRepository      logRepository;
    @MockitoBean KafkaTemplate<?, ?>            kafkaTemplate;

    @Test
    void contextLoads() {
    }
}