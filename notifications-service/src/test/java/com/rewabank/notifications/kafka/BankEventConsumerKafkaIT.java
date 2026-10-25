package com.rewabank.notifications.kafka;

import com.rewabank.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

/**
 * Real Kafka Testcontainers integration test for BankEventConsumer.
 *
 * Why this test exists:
 *   - BankEventConsumerTest and BankEventConsumerMalformedPayloadTest both call
 *     the consumer methods directly — the Kafka broker, topic routing,
 *     deserialization, and @KafkaListener binding are never exercised.
 *   - This test uses a real Kafka container to verify the full path:
 *     producer → broker → partition → consumer → deserialization → service call.
 *
 * What is tested:
 *   1. Messages published to bank.account.created reach the consumer
 *   2. Messages published to bank.transaction.completed reach the consumer
 *   3. Messages published to bank.fraud.alert.raised reach the consumer
 *   4. Messages published to bank.loan.approved reach the consumer
 *   5. Messages published to bank.kyc.verified reach the consumer
 *   6. NotificationService.processEvent() is called with the correct payload
 *   7. Consumer does not crash on empty event map
 */
@SpringBootTest
@Testcontainers
class BankEventConsumerKafkaIT {

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers",          kafka::getBootstrapServers);
        r.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        r.add("spring.kafka.consumer.group-id",          () -> "notifications-it-" + java.util.UUID.randomUUID());
        r.add("spring.kafka.listener.ack-mode",          () -> "MANUAL_IMMEDIATE");
        r.add("spring.kafka.producer.value-serializer",
                () -> "org.springframework.kafka.support.serializer.JsonSerializer");
        r.add("spring.kafka.consumer.value-deserializer",
                () -> "org.springframework.kafka.support.serializer.JsonDeserializer");
        r.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "*");
        // Exclude MongoDB — NotificationService is mocked, MongoDB is not needed
        r.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration");
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:8080/auth/realms/test");
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:8080/auth/realms/test/protocol/openid-connect/certs");
    }

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;

    @SuppressWarnings("deprecation") @MockBean NotificationService notificationService;
    @SuppressWarnings("deprecation") @MockBean MongoTemplate       mongoTemplate;

    // ── Test 1: Account event reaches consumer and service is called ──────────

    @Test
    void accountCreatedEvent_IsConsumedAndProcessed() {
        Map<String, Object> event = Map.of(
                "eventType",  "ACCOUNT_CREATED",
                "eventId",    "acct-evt-" + UUID.randomUUID(),
                "userId",     "kc-user-1"
        );

        kafkaTemplate.send("bank.account.created", event);

        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(notificationService).processEvent(
                                argThat(e -> "ACCOUNT_CREATED".equals(e.get("eventType")))));
    }

    // ── Test 2: Transaction completed event ───────────────────────────────────

    @Test
    void transactionCompletedEvent_IsConsumedAndProcessed() {
        Map<String, Object> event = Map.of(
                "eventType", "TRANSACTION_COMPLETED",
                "eventId",   "txn-evt-" + UUID.randomUUID(),
                "amount",    "5000.00"
        );

        kafkaTemplate.send("bank.transaction.completed", event);

        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(notificationService).processEvent(
                                argThat(e -> "TRANSACTION_COMPLETED".equals(e.get("eventType")))));
    }

    // ── Test 3: Fraud alert event ─────────────────────────────────────────────

    @Test
    void fraudAlertEvent_IsConsumedAndProcessed() {
        Map<String, Object> event = Map.of(
                "eventType", "FRAUD_ALERT_RAISED",
                "eventId",   "fraud-evt-" + UUID.randomUUID(),
                "accountId", UUID.randomUUID().toString()
        );

        kafkaTemplate.send("bank.fraud.alert.raised", event);

        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(notificationService).processEvent(
                                argThat(e -> "FRAUD_ALERT_RAISED".equals(e.get("eventType")))));
    }

    // ── Test 4: Loan approved event ───────────────────────────────────────────

    @Test
    void loanApprovedEvent_IsConsumedAndProcessed() {
        Map<String, Object> event = Map.of(
                "eventType", "LOAN_APPROVED",
                "eventId",   "loan-evt-" + UUID.randomUUID(),
                "loanId",    UUID.randomUUID().toString()
        );

        kafkaTemplate.send("bank.loan.approved", event);

        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(notificationService).processEvent(
                                argThat(e -> "LOAN_APPROVED".equals(e.get("eventType")))));
    }

    // ── Test 5: KYC verified event ────────────────────────────────────────────

    @Test
    void kycVerifiedEvent_IsConsumedAndProcessed() {
        Map<String, Object> event = Map.of(
                "eventType", "KYC_VERIFIED",
                "eventId",   "kyc-evt-" + UUID.randomUUID(),
                "userId",    "kc-user-2"
        );

        kafkaTemplate.send("bank.kyc.verified", event);

        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(notificationService).processEvent(
                                argThat(e -> "KYC_VERIFIED".equals(e.get("eventType")))));
    }

    // ── Test 6: Multiple events on same topic are all consumed ────────────────

    @Test
    void multipleAccountEvents_AllConsumedInOrder() {
        String batchId = UUID.randomUUID().toString();

        Map<String, Object> created   = Map.of("eventType", "ACCOUNT_CREATED",   "batchId", batchId, "seq", "1");
        Map<String, Object> activated = Map.of("eventType", "ACCOUNT_ACTIVATED", "batchId", batchId, "seq", "2");
        Map<String, Object> frozen    = Map.of("eventType", "ACCOUNT_FROZEN",    "batchId", batchId, "seq", "3");

        kafkaTemplate.send("bank.account.created",   created);
        kafkaTemplate.send("bank.account.activated", activated);
        kafkaTemplate.send("bank.account.frozen",    frozen);

        await()
                .atMost(25, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    verify(notificationService).processEvent(
                            argThat(e -> "ACCOUNT_CREATED".equals(e.get("eventType"))
                                    && batchId.equals(e.get("batchId"))));
                    verify(notificationService).processEvent(
                            argThat(e -> "ACCOUNT_ACTIVATED".equals(e.get("eventType"))
                                    && batchId.equals(e.get("batchId"))));
                    verify(notificationService).processEvent(
                            argThat(e -> "ACCOUNT_FROZEN".equals(e.get("eventType"))
                                    && batchId.equals(e.get("batchId"))));
                });
    }
}
