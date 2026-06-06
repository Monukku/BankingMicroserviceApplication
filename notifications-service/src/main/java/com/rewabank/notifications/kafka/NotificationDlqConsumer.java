package com.rewabank.notifications.kafka;

import com.rewabank.notifications.document.NotificationLog;
import com.rewabank.notifications.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes messages that exhausted all retries from @RetryableTopic DLQs.
 * Persists a FAILED log entry so delivery failures are visible in the
 * notification_logs collection — prevents silent data loss.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDlqConsumer {

    private final NotificationLogRepository logRepository;

    @KafkaListener(
            topics = {
                    "bank.account.created.dlq",
                    "bank.account.activated.dlq",
                    "bank.account.frozen.dlq",
                    "bank.account.closed.dlq",
                    "bank.account.dormant.dlq",
                    "bank.transaction.completed.dlq",
                    "bank.transaction.failed.dlq",
                    "bank.transaction.reversed.dlq",
                    "bank.balance.updated.dlq",
                    "bank.fraud.alert.raised.dlq",
                    "bank.fraud.alert.cleared.dlq",
                    "bank.loan.approved.dlq",
                    "bank.loan.rejected.dlq",
                    "bank.loan.disbursed.dlq",
                    "bank.card.issued.dlq",
                    "bank.card.blocked.dlq",
                    "bank.user.registered.dlq",
                    "bank.user.locked.dlq",
                    "bank.kyc.verified.dlq"
            },
            groupId = "notifications-ms-dlq"
    )
    public void handleDlqEvent(
            Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        log.error("Notification DLQ — undeliverable event from topic={} payload={}",
                topic, event);

        try {
            String eventId   = (String) event.getOrDefault("eventId", UUID.randomUUID().toString());
            String eventType = (String) event.getOrDefault("eventType", "UNKNOWN");
            String userId    = (String) event.getOrDefault("keycloakUserId", "unknown");

            NotificationLog failedLog = NotificationLog.builder()
                    .eventId(eventId + "-dlq")   // suffix to avoid idempotency collision
                    .eventType(eventType)
                    .keycloakUserId(userId)
                    .channel("DLQ")
                    .recipient("N/A")
                    .message("Unprocessable event after all retries: " + topic)
                    .status("FAILED")
                    .failureReason("Event exhausted retries and landed in DLQ: " + topic)
                    .sentAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();

            logRepository.save(failedLog);
            log.warn("DLQ failure persisted to notification_logs: eventId={} topic={}",
                    eventId, topic);

        } catch (Exception e) {
            log.error("Failed to persist DLQ notification log for topic={}: {}",
                    topic, e.getMessage());
        }
    }
}
