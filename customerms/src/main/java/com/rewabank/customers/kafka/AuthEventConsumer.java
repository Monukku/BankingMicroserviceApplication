package com.rewabank.customers.kafka;

import com.rewabank.customers.entity.Customer;
import com.rewabank.customers.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventConsumer {

    private final CustomerRepository customerRepository;

    // Consumes bank.user.registered — creates KYC profile automatically
    // 3-tier DLQ: bank.user.registered → bank.user.registered.retry → bank.user.registered.dlq
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = "${kafka.topics.user-registered:bank.user.registered}",
            groupId = "${kafka.consumer.group-id:customers-ms}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserRegistered(Map<String, Object> event) {
        String keycloakUserId = (String) event.get("keycloakUserId");
        String email          = (String) event.get("email");
        String mobileNumber   = (String) event.get("mobileNumber");
        String fullName       = (String) event.get("fullName");

        log.info("Received USER_REGISTERED event for: {}", email);

        // Idempotent — skip if profile already exists
        if (customerRepository.existsByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)) {
            log.warn("Customer profile already exists for keycloakUserId: {} — skipping", keycloakUserId);
            return;
        }

        Customer customer = Customer.builder()
                .keycloakUserId(keycloakUserId)
                .email(email)
                .mobileNumber(mobileNumber)
                .fullName(fullName)
                .kycStatus(Customer.KycStatus.NOT_SUBMITTED)
                .build();

        customerRepository.save(customer);
        log.info("Customer KYC profile created for: {} id: {}", email, customer.getId());
    }
}
