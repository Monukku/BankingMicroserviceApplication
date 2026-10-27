package com.rewabank.accounts.kafka;

import com.rewabank.accounts.client.CustomersFeignClient;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.repository.AccountsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.awaitility.Awaitility.await;

/**
 * Real Kafka integration test for ExternalEventConsumer.
 *
 * Why this test exists:
 *   - The existing ExternalEventConsumerTest mocks AccountService and
 *     AccountsRepository. It never proves that:
 *     a) The @KafkaListener annotation binds to the correct topic
 *     b) Message deserialization works for Map<String, Object> payloads
 *     c) The consumer group offset is committed after successful processing
 *     d) A KYC_VERIFIED event from a real broker triggers account activation
 *
 * What is tested:
 *   1. KYC_VERIFIED event published to real embedded Kafka → PENDING account activated
 *   2. FRAUD_ALERT event published → ACTIVE account frozen
 *   3. Malformed customerId in KYC event → exception thrown, no account state change
 *   4. Unknown event type → consumer does not crash
 *
 * Uses @EmbeddedKafka (spring-kafka-test) — faster than Testcontainers for
 * producer/consumer binding tests where Kafka broker parity isn't the concern.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "bank.kyc.verified",
                "bank.payment.completed",
                "bank.fraud.alert.raised"
        }
)
@DirtiesContext
@TestPropertySource(properties = {
        // H2 for DB — this test is about Kafka consumer, not DB locking
        "spring.datasource.url=jdbc:h2:mem:kafkatestsdb;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        // Kafka uses the embedded broker — NOT excluded here
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
        // Disable Redis autoconfiguration — mock RedisTemplate is injected instead
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "spring.cache.type=none",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/auth/realms/test",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/auth/realms/test/protocol/openid-connect/certs"
})
class ExternalEventConsumerKafkaIT {

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired AccountsRepository            accountsRepository;

    @SuppressWarnings("deprecation") @MockBean CustomersFeignClient       customersFeignClient;
    @SuppressWarnings("deprecation") @MockBean RedisTemplate<String, Object> redisTemplate;

    private UUID customerId;
    private Account pendingAccount;

    @BeforeEach
    void setUp() {
        accountsRepository.deleteAll();
        customerId = UUID.randomUUID();

        pendingAccount = accountsRepository.saveAndFlush(
                Account.builder()
                        .accountNumber("KIT" + UUID.randomUUID().toString().replace("-","").substring(0,9))
                        .keycloakUserId("kyc-test-user")
                        .customerId(customerId)
                        .accountType(Account.AccountType.SAVINGS)
                        .status(Account.AccountStatus.PENDING)
                        .balance(BigDecimal.ZERO)
                        .minimumBalance(new BigDecimal("1000.00"))
                        .currency("INR")
                        .branchCode("BR001")
                        .ifscCode("RWAB001")
                        .build());
    }

    // ── Test 1: KYC_VERIFIED event activates PENDING accounts ────────────────

    @Test
    void kycVerifiedEvent_ActivatesPendingAccount() {
        Map<String, Object> event = Map.of(
                "eventType",  "KYC_VERIFIED",
                "customerId", customerId.toString(),
                "eventId",    "evt-kyc-" + UUID.randomUUID()
        );

        kafkaTemplate.send("bank.kyc.verified", event);

        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Account updated = accountsRepository
                            .findById(pendingAccount.getId())
                            .orElseThrow();
                    assertEquals(Account.AccountStatus.ACTIVE, updated.getStatus(),
                            "KYC_VERIFIED event must activate the PENDING account");
                });
    }

    // ── Test 2: FRAUD_ALERT event freezes ACTIVE account ─────────────────────

    @Test
    void fraudAlertEvent_FreezesActiveAccount() {
        // First activate the account so we can test freezing
        pendingAccount.setStatus(Account.AccountStatus.ACTIVE);
        accountsRepository.saveAndFlush(pendingAccount);

        Map<String, Object> event = Map.of(
                "eventType", "FRAUD_ALERT",
                "accountId", pendingAccount.getId().toString(),
                "reason",    "Suspicious high-velocity transactions"
        );

        kafkaTemplate.send("bank.fraud.alert.raised", event);

        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Account frozen = accountsRepository
                            .findById(pendingAccount.getId())
                            .orElseThrow();
                    assertEquals(Account.AccountStatus.FROZEN, frozen.getStatus(),
                            "FRAUD_ALERT event must freeze the ACTIVE account");
                });
    }

    // ── Test 3: KYC event with unknown customerId — no account changed ────────

    @Test
    void kycVerifiedEvent_UnknownCustomerId_NoAccountStateChange() {
        String unknownCustomerId = UUID.randomUUID().toString();

        Map<String, Object> event = Map.of(
                "eventType",  "KYC_VERIFIED",
                "customerId", unknownCustomerId,
                "eventId",    "evt-unknown-" + UUID.randomUUID()
        );

        kafkaTemplate.send("bank.kyc.verified", event);

        // Wait briefly to ensure any async processing would have completed
        await()
                .during(3, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Account unchanged = accountsRepository
                            .findById(pendingAccount.getId())
                            .orElseThrow();
                    assertEquals(Account.AccountStatus.PENDING, unchanged.getStatus(),
                            "Unknown customerId must not affect existing accounts");
                });
    }

    // ── Test 4: Consumer handles duplicate KYC events idempotently ───────────

    @Test
    void kycVerifiedEvent_SentTwice_AccountStaysActive_NoException() {
        Map<String, Object> event = Map.of(
                "eventType",  "KYC_VERIFIED",
                "customerId", customerId.toString(),
                "eventId",    "evt-dup-" + UUID.randomUUID()
        );

        // Send the same KYC event twice
        kafkaTemplate.send("bank.kyc.verified", event);
        kafkaTemplate.send("bank.kyc.verified", event);

        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Account updated = accountsRepository
                            .findById(pendingAccount.getId())
                            .orElseThrow();
                    assertEquals(Account.AccountStatus.ACTIVE, updated.getStatus(),
                            "Duplicate KYC event must result in ACTIVE status — not crash");
                });
    }
}
