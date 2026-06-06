package com.rewabank.fraud.kafka;

import com.rewabank.fraud.service.FraudAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes messages that exhausted all retries from @RetryableTopic DLQs.
 * Each DLQ message means fraud analysis failed permanently for that event —
 * raises a BLOCK alert so the account is reviewed manually.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudDlqConsumer {

    private final FraudAlertService fraudAlertService;

    @KafkaListener(
            topics = {
                    "bank.transaction.completed.dlq",
                    "bank.balance.updated.dlq"
            },
            groupId = "fraud-ms-dlq"
    )
    public void handleDlqEvent(
            Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        log.error("Unprocessable event landed in DLQ topic={} payload={}", topic, event);

        // transaction events carry sourceAccountId; balance events carry accountId
        String accountId = (String) event.get("accountId");
        if (accountId == null) {
            accountId = (String) event.get("sourceAccountId");
        }

        if (accountId == null) {
            log.error("DLQ event has no accountId — cannot raise alert. topic={} payload={}", topic, event);
            return;
        }

        try {
            String amount = String.valueOf(event.getOrDefault("amount", "0"));
            fraudAlertService.createAlert(
                    UUID.fromString(accountId),
                    (String) event.get("keycloakUserId"),
                    (String) event.get("transactionId"),
                    new BigDecimal(amount),
                    99,
                    "BLOCK",
                    "DLQ_UNPROCESSABLE_EVENT(topic=" + topic + ")"
            );
        } catch (Exception e) {
            log.error("Failed to raise DLQ fraud alert for account={}: {}", accountId, e.getMessage());
        }
    }
}
