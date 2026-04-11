package com.rewabank.transactions.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Direct Kafka producer for transaction events.
 * Note: Critical events go through OutboxPublisherService for
 * guaranteed delivery. This producer handles supplementary
 * informational events only.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.initiated:bank.transaction.initiated}")
    private String initiatedTopic;

    @Value("${kafka.topics.completed:bank.transaction.completed}")
    private String completedTopic;

    @Value("${kafka.topics.failed:bank.transaction.failed}")
    private String failedTopic;

    @Value("${kafka.topics.reversed:bank.transaction.reversed}")
    private String reversedTopic;

    public void publishInitiated(String transactionId, String sourceAccountNumber,
                                 String destinationAccountNumber,
                                 String amount, String transactionType) {
        publish(initiatedTopic, transactionId, Map.of(
                "eventId",                    UUID.randomUUID().toString(),
                "eventType",                  "TRANSACTION_INITIATED",
                "transactionId",              transactionId,
                "sourceAccountNumber",        sourceAccountNumber,
                "destinationAccountNumber",   destinationAccountNumber,
                "amount",                     amount,
                "transactionType",            transactionType,
                "occurredAt",                 LocalDateTime.now().toString()
        ));
    }

    public void publishCompleted(String transactionId, String sourceAccountNumber,
                                 String destinationAccountNumber,
                                 String amount, String referenceNumber) {
        publish(completedTopic, transactionId, Map.of(
                "eventId",                    UUID.randomUUID().toString(),
                "eventType",                  "TRANSACTION_COMPLETED",
                "transactionId",              transactionId,
                "sourceAccountNumber",        sourceAccountNumber,
                "destinationAccountNumber",   destinationAccountNumber,
                "amount",                     amount,
                "referenceNumber",            referenceNumber != null ? referenceNumber : "",
                "occurredAt",                 LocalDateTime.now().toString()
        ));
    }

    public void publishFailed(String transactionId, String reason,
                              String sourceAccountNumber) {
        publish(failedTopic, transactionId, Map.of(
                "eventId",             UUID.randomUUID().toString(),
                "eventType",           "TRANSACTION_FAILED",
                "transactionId",       transactionId,
                "sourceAccountNumber", sourceAccountNumber,
                "reason",              reason != null ? reason : "",
                "occurredAt",          LocalDateTime.now().toString()
        ));
    }

    public void publishReversed(String transactionId, String reason,
                                String sourceAccountNumber,
                                String amount) {
        publish(reversedTopic, transactionId, Map.of(
                "eventId",             UUID.randomUUID().toString(),
                "eventType",           "TRANSACTION_REVERSED",
                "transactionId",       transactionId,
                "sourceAccountNumber", sourceAccountNumber,
                "amount",              amount,
                "reason",              reason != null ? reason : "",
                "occurredAt",          LocalDateTime.now().toString()
        ));
    }

    private void publish(String topic, String key, Map<String, Object> payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to {}: {}", topic, ex.getMessage());
                    } else {
                        log.debug("Published to {} offset: {}",
                                topic, result.getRecordMetadata().offset());
                    }
                });
    }
}
