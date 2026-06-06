package com.rewabank.notifications.kafka;

import com.rewabank.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankEventConsumerTest {

    @Mock NotificationService notificationService;
    @Mock Acknowledgment      ack;

    @InjectMocks BankEventConsumer consumer;

    private static final Map<String, Object> ACCOUNT_EVENT = Map.of(
            "eventType",      "ACCOUNT_ACTIVATED",
            "eventId",        "evt-001",
            "keycloakUserId", "kc-user-1"
    );

    // ── success path ─────────────────────────────────────────────

    @Test
    void handleAccountEvents_success_acknowledgesOffset() {
        consumer.handleAccountEvents(ACCOUNT_EVENT, ack);

        verify(notificationService).processEvent(ACCOUNT_EVENT);
        verify(ack).acknowledge();
    }

    @Test
    void handleTransactionEvents_success_acknowledgesOffset() {
        Map<String, Object> event = Map.of("eventType", "TRANSACTION_COMPLETED", "eventId", "evt-002");
        consumer.handleTransactionEvents(event, ack);

        verify(notificationService).processEvent(event);
        verify(ack).acknowledge();
    }

    @Test
    void handleFraudEvents_success_acknowledgesOffset() {
        Map<String, Object> event = Map.of("eventType", "FRAUD_ALERT_RAISED", "eventId", "evt-003");
        consumer.handleFraudEvents(event, ack);

        verify(notificationService).processEvent(event);
        verify(ack).acknowledge();
    }

    @Test
    void handleUserEvents_success_acknowledgesOffset() {
        Map<String, Object> event = Map.of("eventType", "KYC_VERIFIED", "eventId", "evt-004");
        consumer.handleUserEvents(event, ack);

        verify(notificationService).processEvent(event);
        verify(ack).acknowledge();
    }

    // ── failure path — no ACK so Kafka retries ───────────────────

    @Test
    void handleAccountEvents_serviceThrows_doesNotAcknowledge() {
        doThrow(new RuntimeException("DB down")).when(notificationService).processEvent(any());

        assertThatThrownBy(() -> consumer.handleAccountEvents(ACCOUNT_EVENT, ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");

        verify(ack, never()).acknowledge();
    }

    @Test
    void handleTransactionEvents_serviceThrows_doesNotAcknowledge() {
        Map<String, Object> event = Map.of("eventType", "TRANSACTION_FAILED", "eventId", "evt-005");
        doThrow(new RuntimeException("timeout")).when(notificationService).processEvent(any());

        assertThatThrownBy(() -> consumer.handleTransactionEvents(event, ack))
                .isInstanceOf(RuntimeException.class);

        verify(ack, never()).acknowledge();
    }

    @Test
    void handleLoanEvents_success_acknowledgesOffset() {
        Map<String, Object> event = Map.of("eventType", "LOAN_APPROVED", "eventId", "evt-006");
        consumer.handleLoanEvents(event, ack);

        verify(notificationService).processEvent(event);
        verify(ack).acknowledge();
    }

    @Test
    void handleCardEvents_success_acknowledgesOffset() {
        Map<String, Object> event = Map.of("eventType", "CARD_ISSUED", "eventId", "evt-007");
        consumer.handleCardEvents(event, ack);

        verify(notificationService).processEvent(event);
        verify(ack).acknowledge();
    }
}