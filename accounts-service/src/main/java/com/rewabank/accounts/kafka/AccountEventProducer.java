package com.rewabank.accounts.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    private static final String FIELD_EVENT_ID   = "eventId";
    private static final String FIELD_OCCURRED_AT = "occurredAt";
    private static final String FIELD_ACCOUNT_ID  = "accountId";
    private static final String FIELD_ACCOUNT_NUM = "accountNumber";
    private static final String FIELD_CUSTOMER_ID = "customerId";

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
                FIELD_EVENT_ID,       UUID.randomUUID().toString(),
                "eventType",     "ACCOUNT_CREATED",
                FIELD_ACCOUNT_ID,     accountId,
                FIELD_ACCOUNT_NUM, accountNumber,
                FIELD_CUSTOMER_ID,    customerId,
                "accountType",   accountType,
                FIELD_OCCURRED_AT,    LocalDateTime.now(ZoneOffset.UTC).toString()
        ));
    }

    public void publishAccountActivated(String accountId, String accountNumber,
                                        String customerId) {
        publish(accountActivatedTopic, accountId, Map.of(
                FIELD_EVENT_ID,       UUID.randomUUID().toString(),
                "eventType",     "ACCOUNT_ACTIVATED",
                FIELD_ACCOUNT_ID,     accountId,
                FIELD_ACCOUNT_NUM, accountNumber,
                FIELD_CUSTOMER_ID,    customerId,
                FIELD_OCCURRED_AT,    LocalDateTime.now(ZoneOffset.UTC).toString()
        ));
    }

    public void publishAccountFrozen(String accountId, String accountNumber,
                                     String reason) {
        publish(accountFrozenTopic, accountId, Map.of(
                FIELD_EVENT_ID,       UUID.randomUUID().toString(),
                "eventType",     "ACCOUNT_FROZEN",
                FIELD_ACCOUNT_ID,     accountId,
                FIELD_ACCOUNT_NUM, accountNumber,
                "reason",        reason != null ? reason : "",
                FIELD_OCCURRED_AT,    LocalDateTime.now(ZoneOffset.UTC).toString()
        ));
    }

    public void publishAccountClosed(String accountId, String accountNumber,
                                     String customerId) {
        publish(accountClosedTopic, accountId, Map.of(
                FIELD_EVENT_ID,       UUID.randomUUID().toString(),
                "eventType",     "ACCOUNT_CLOSED",
                FIELD_ACCOUNT_ID,     accountId,
                FIELD_ACCOUNT_NUM, accountNumber,
                FIELD_CUSTOMER_ID,    customerId,
                FIELD_OCCURRED_AT,    LocalDateTime.now(ZoneOffset.UTC).toString()
        ));
    }

    public void publishBalanceUpdated(String accountId, String accountNumber,
                                      String direction, String amount,
                                      String newBalance, String correlationId) {
        publish(balanceUpdatedTopic, accountId, Map.of(
                FIELD_EVENT_ID,       UUID.randomUUID().toString(),
                "eventType",     "BALANCE_UPDATED",
                FIELD_ACCOUNT_ID,     accountId,
                FIELD_ACCOUNT_NUM, accountNumber,
                "direction",     direction,   // CREDIT or DEBIT
                "amount",        amount,
                "newBalance",    newBalance,
                "correlationId", correlationId != null ? correlationId : "",
                FIELD_OCCURRED_AT,    LocalDateTime.now(ZoneOffset.UTC).toString()
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
