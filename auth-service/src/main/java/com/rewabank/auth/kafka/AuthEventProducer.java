package com.rewabank.auth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.user-registered:bank.user.registered}")
    private String userRegisteredTopic;

    @Value("${kafka.topics.user-locked:bank.user.locked}")
    private String userLockedTopic;

    public void publishUserRegistered(String keycloakUserId, String email,
                                      String mobileNumber, String fullName) {
        Map<String, Object> event = Map.of(
                "eventId",        UUID.randomUUID().toString(),
                "eventType",      "USER_REGISTERED",
                "keycloakUserId", keycloakUserId,
                "email",          email,
                "mobileNumber",   mobileNumber,
                "fullName",       fullName,
                "occurredAt",     LocalDateTime.now().toString()
        );

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(userRegisteredTopic, keycloakUserId, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish USER_REGISTERED event for {}: {}", email, ex.getMessage());
                // Outbox pattern handles retry — event saved to outbox table
            } else {
                log.info("USER_REGISTERED event published for {} offset: {}",
                        email, result.getRecordMetadata().offset());
            }
        });
    }

    public void publishUserLocked(String keycloakUserId, String reason) {
        Map<String, Object> event = Map.of(
                "eventId",        UUID.randomUUID().toString(),
                "eventType",      "USER_LOCKED",
                "keycloakUserId", keycloakUserId,
                "reason",         reason,
                "occurredAt",     LocalDateTime.now().toString()
        );

        kafkaTemplate.send(userLockedTopic, keycloakUserId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish USER_LOCKED event: {}", ex.getMessage());
                    } else {
                        log.info("USER_LOCKED event published for {}", keycloakUserId);
                    }
                });
    }
}
