package com.rewabank.fraud.kafka;
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
public class FraudEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Value("${kafka.topics.alert-raised:bank.fraud.alert.raised}")
    private String alertRaisedTopic;
    @Value("${kafka.topics.alert-cleared:bank.fraud.alert.cleared}")
    private String alertClearedTopic;
    public void publishAlertRaised(String alertId, String accountId,
                                   String keycloakUserId, String transactionId,
                                   String amount, int score,
                                   String action, String triggeredRules) {
        Map<String, Object> event = Map.ofEntries(
                Map.entry("eventId",        UUID.randomUUID().toString()),
                Map.entry("eventType",      "FRAUD_ALERT_RAISED"),
                Map.entry("alertId",        alertId),
                Map.entry("accountId",      accountId),
                Map.entry("keycloakUserId", keycloakUserId != null ? keycloakUserId : ""),
                Map.entry("transactionId",  transactionId != null ? transactionId : ""),
                Map.entry("amount",         amount),
                Map.entry("fraudScore",     score),
                Map.entry("action",         action),
                Map.entry("triggeredRules", triggeredRules),
                Map.entry("occurredAt",     LocalDateTime.now().toString())
        );
        kafkaTemplate.send(alertRaisedTopic, accountId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish FRAUD_ALERT_RAISED: {}",
                                ex.getMessage());
                    } else {
                        log.info("FRAUD_ALERT_RAISED published for account: {}",
                                accountId);
                    }
                });
    }
    public void publishAlertCleared(String alertId, String accountId,
                                    String resolution) {
        Map<String, Object> event = Map.of(
                "eventId",    UUID.randomUUID().toString(),
                "eventType",  "FRAUD_ALERT_CLEARED",
                "alertId",    alertId,
                "accountId",  accountId,
                "resolution", resolution,
                "occurredAt", LocalDateTime.now().toString()
        );
        kafkaTemplate.send(alertClearedTopic, accountId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish FRAUD_ALERT_CLEARED: {}",
                                ex.getMessage());
                    }
                });
    }
}
