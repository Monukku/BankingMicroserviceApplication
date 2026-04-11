package com.rewabank.audit.kafka;

import com.rewabank.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes ALL bank.* topics — single consumer for all events.
 * Append-only writes to PostgreSQL audit_log table.
 * Idempotent — duplicate events safely skipped.
 * DLQ ensures no event ever lost permanently.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AllEventConsumer {

    private final AuditService auditService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            dltTopicSuffix = ".audit.dlq",
            retryTopicSuffix = ".audit.retry"
    )
    @KafkaListener(
            topics = {
                    "bank.account.created",
                    "bank.account.activated",
                    "bank.account.frozen",
                    "bank.account.closed",
                    "bank.account.dormant",
                    "bank.balance.updated",
                    "bank.transaction.initiated",
                    "bank.transaction.completed",
                    "bank.transaction.failed",
                    "bank.transaction.reversed",
                    "bank.user.registered",
                    "bank.user.locked",
                    "bank.kyc.verified",
                    "bank.customer.updated",
                    "bank.loan.approved",
                    "bank.loan.rejected",
                    "bank.loan.disbursed",
                    "bank.repayment.completed",
                    "bank.repayment.overdue",
                    "bank.payment.success",
                    "bank.payment.failed",
                    "bank.fraud.alert.raised",
                    "bank.fraud.alert.cleared",
                    "bank.card.issued",
                    "bank.card.blocked"
            },
            groupId = "audit-ms"
    )
    public void consumeAll(
            Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.debug("Audit consuming from topic: {} eventType: {}",
                    topic, event.get("eventType"));
            auditService.record(event, topic);
        } catch (Exception e) {
            log.error("Audit failed for topic: {} error: {} — will retry",
                    topic, e.getMessage());
            throw e;
        }
    }
}
