package com.rewabank.notifications.kafka;

import com.rewabank.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes ALL bank.* topics.
 * 3-tier DLQ per topic — @RetryableTopic handles routing automatically.
 * Manual ACK — only commits offset after successful processing.
 * Being down does NOT affect any other MS — Kafka buffers all events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BankEventConsumer {

    private final NotificationService notificationService;

    // Account events
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = {
                    "bank.account.created",
                    "bank.account.activated",
                    "bank.account.frozen",
                    "bank.account.closed",
                    "bank.account.dormant"
            },
            groupId = "notifications-ms-accounts"
    )
    public void handleAccountEvents(Map<String, Object> event,
                                    Acknowledgment ack) {
        process(event, ack);
    }

    // Transaction events
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = {
                    "bank.transaction.completed",
                    "bank.transaction.failed",
                    "bank.transaction.reversed"
            },
            groupId = "notifications-ms-transactions"
    )
    public void handleTransactionEvents(Map<String, Object> event,
                                        Acknowledgment ack) {
        process(event, ack);
    }

    // Balance events — low balance alert
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = "bank.balance.updated",
            groupId = "notifications-ms-balance"
    )
    public void handleBalanceEvents(Map<String, Object> event,
                                    Acknowledgment ack) {
        process(event, ack);
    }

    // Fraud events
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 500),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = {
                    "bank.fraud.alert.raised",
                    "bank.fraud.alert.cleared"
            },
            groupId = "notifications-ms-fraud"
    )
    public void handleFraudEvents(Map<String, Object> event,
                                  Acknowledgment ack) {
        process(event, ack);
    }

    // Loan events
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = {
                    "bank.loan.approved",
                    "bank.loan.rejected",
                    "bank.loan.disbursed",
                    "bank.repayment.completed",
                    "bank.repayment.overdue"
            },
            groupId = "notifications-ms-loans"
    )
    public void handleLoanEvents(Map<String, Object> event,
                                 Acknowledgment ack) {
        process(event, ack);
    }

    // Payment events
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = {
                    "bank.payment.success",
                    "bank.payment.failed"
            },
            groupId = "notifications-ms-payments"
    )
    public void handlePaymentEvents(Map<String, Object> event,
                                    Acknowledgment ack) {
        process(event, ack);
    }

    // Card events
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = {
                    "bank.card.issued",
                    "bank.card.blocked"
            },
            groupId = "notifications-ms-cards"
    )
    public void handleCardEvents(Map<String, Object> event,
                                 Acknowledgment ack) {
        process(event, ack);
    }

    // User events
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = {
                    "bank.user.registered",
                    "bank.user.locked",
                    "bank.kyc.verified"
            },
            groupId = "notifications-ms-users"
    )
    public void handleUserEvents(Map<String, Object> event,
                                 Acknowledgment ack) {
        process(event, ack);
    }

    private void process(Map<String, Object> event, Acknowledgment ack) {
        try {
            String eventType = (String) event.getOrDefault("eventType", "UNKNOWN");
            log.debug("Processing notification for event: {}", eventType);
            notificationService.processEvent(event);
            ack.acknowledge();  // manual ACK only on success
        } catch (Exception e) {
            log.error("Failed to process notification event: {} — will retry",
                    e.getMessage());
            // Do NOT ack — @RetryableTopic will retry then DLQ
            throw e;
        }
    }
}
