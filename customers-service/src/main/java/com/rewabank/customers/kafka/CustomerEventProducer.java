package com.rewabank.customers.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.kyc-verified:bank.kyc.verified}")
    private String kycVerifiedTopic;

    @Value("${kafka.topics.customer-updated:bank.customer.updated}")
    private String customerUpdatedTopic;

    // Accounts MS consumes this to auto-activate PENDING account
    public void publishKycVerified(String customerId, String keycloakUserId,
                                   String verifiedBy) {
        Map<String, Object> event = Map.of(
                "eventId",        UUID.randomUUID().toString(),
                "eventType",      "KYC_VERIFIED",
                "customerId",     customerId,
                "keycloakUserId", keycloakUserId,
                "verifiedBy",     verifiedBy,
                "occurredAt",     LocalDateTime.now().toString()
        );

        kafkaTemplate.send(kycVerifiedTopic, customerId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish KYC_VERIFIED for {}: {}", customerId, ex.getMessage());
                    } else {
                        log.info("KYC_VERIFIED published for customer {}", customerId);
                    }
                });
    }

    public void publishCustomerUpdated(String customerId, String eventType) {
        Map<String, Object> event = Map.of(
                "eventId",    UUID.randomUUID().toString(),
                "eventType",  eventType,
                "customerId", customerId,
                "occurredAt", LocalDateTime.now().toString()
        );

        kafkaTemplate.send(customerUpdatedTopic, customerId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for {}: {}", eventType, customerId, ex.getMessage());
                    }
                });
    }
}
