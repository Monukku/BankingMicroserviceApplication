package com.rewabank.accounts.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Direct Kafka producer for non-critical account events.
 * For guaranteed delivery — use OutboxPublisherService instead.
 * This is used for lightweight informational events only.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.account-created:bank.account.created}")
    private String accountCreatedTopic;

    @Value("${kafka.topics.account-activated:bank.account.activated}")
    private String accountActivatedTopic;

    @Value("${kafka.topics.account-frozen:bank.account.frozen}")
    private String accountFrozenTopic;

    @Value("${kafka.topics.account-closed:bank.account.closed}")
    private String accountClosedTopic;

    @Value("${kafka.topics.balance-updated:bank.balance.updated}")
    private String balanceUpdatedTopic;

    public void publishAccountCreated(String accountId, String accountNumber,
                                      String customerId, String accountType) {
        publish(accountCreatedTopic, accountId, Map.of(
                "eventId",       UUID.randomUUID().toString(),
                "eventType",     "ACCOUNT_CREATED",
                "accountId",     accountId,
                "accountNumber", accountNumber,
                "customerId",    customerId,
                "accountType",   accountType,
                "occurredAt",    LocalDateTime.now().toString()
        ));
    }

    public void publishAccountActivated(String accountId, String accountNumber,
                                        String customerId) {
        publish(accountActivatedTopic, accountId, Map.of(
                "eventId",       UUID.randomUUID().toString(),
                "eventType",     "ACCOUNT_ACTIVATED",
                "accountId",     accountId,
                "accountNumber", accountNumber,
                "customerId",    customerId,
                "occurredAt",    LocalDateTime.now().toString()
        ));
    }

    public void publishAccountFrozen(String accountId, String accountNumber,
                                     String reason) {
        publish(accountFrozenTopic, accountId, Map.of(
                "eventId",       UUID.randomUUID().toString(),
                "eventType",     "ACCOUNT_FROZEN",
                "accountId",     accountId,
                "accountNumber", accountNumber,
                "reason",        reason != null ? reason : "",
                "occurredAt",    LocalDateTime.now().toString()
        ));
    }

    public void publishAccountClosed(String accountId, String accountNumber,
                                     String customerId) {
        publish(accountClosedTopic, accountId, Map.of(
                "eventId",       UUID.randomUUID().toString(),
                "eventType",     "ACCOUNT_CLOSED",
                "accountId",     accountId,
                "accountNumber", accountNumber,
                "customerId",    customerId,
                "occurredAt",    LocalDateTime.now().toString()
        ));
    }

    public void publishBalanceUpdated(String accountId, String accountNumber,
                                      String direction, String amount,
                                      String newBalance, String correlationId) {
        publish(balanceUpdatedTopic, accountId, Map.of(
                "eventId",       UUID.randomUUID().toString(),
                "eventType",     "BALANCE_UPDATED",
                "accountId",     accountId,
                "accountNumber", accountNumber,
                "direction",     direction,   // CREDIT or DEBIT
                "amount",        amount,
                "newBalance",    newBalance,
                "correlationId", correlationId != null ? correlationId : "",
                "occurredAt",    LocalDateTime.now().toString()
        ));
    }

    private void publish(String topic, String key, Map<String, Object> payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to topic {}: {}", topic, ex.getMessage());
                    } else {
                        log.debug("Published to {} offset: {}",
                                topic, result.getRecordMetadata().offset());
                    }
                });
    }
}
//
//
//        ---
//
//        **Important — how this fits with OutboxPublisherService:**
//        ```
//AccountEventProducer     → fire-and-forget, best-effort
//used for low-stakes events
//        (balance updates to Notifications MS etc.)
//
//OutboxPublisherService   → guaranteed delivery via DB outbox
//used for critical state changes
//        (account created, frozen, closed)
