package com.rewabank.notifications.kafka;

import com.rewabank.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Negative tests for BankEventConsumer — malformed / adversarial Kafka payloads.
 * Verifies: consumer crash safety, offset commit behaviour, and DLQ routing
 * invariant (no ACK on failure so @RetryableTopic can reroute to DLQ).
 */
@ExtendWith(MockitoExtension.class)
class BankEventConsumerMalformedPayloadTest {

    @Mock NotificationService notificationService;
    @Mock Acknowledgment      ack;

    @InjectMocks BankEventConsumer consumer;

    // ── Rule: Empty map must not throw — eventType defaults to UNKNOWN ────────

    @Test
    void handleAccountEvents_EmptyMap_DoesNotThrow_AcknowledgesOffset() {
        consumer.handleAccountEvents(Collections.emptyMap(), ack);

        verify(notificationService).processEvent(Collections.emptyMap());
        verify(ack).acknowledge();
    }

    @Test
    void handleTransactionEvents_EmptyMap_DoesNotThrow() {
        consumer.handleTransactionEvents(Collections.emptyMap(), ack);
        verify(ack).acknowledge();
    }

    @Test
    void handleFraudEvents_EmptyMap_DoesNotThrow() {
        consumer.handleFraudEvents(Collections.emptyMap(), ack);
        verify(ack).acknowledge();
    }

    @Test
    void handleLoanEvents_EmptyMap_DoesNotThrow() {
        consumer.handleLoanEvents(Collections.emptyMap(), ack);
        verify(ack).acknowledge();
    }

    @Test
    void handleCardEvents_EmptyMap_DoesNotThrow() {
        consumer.handleCardEvents(Collections.emptyMap(), ack);
        verify(ack).acknowledge();
    }

    @Test
    void handleUserEvents_EmptyMap_DoesNotThrow() {
        consumer.handleUserEvents(Collections.emptyMap(), ack);
        verify(ack).acknowledge();
    }

    @Test
    void handlePaymentEvents_EmptyMap_DoesNotThrow() {
        consumer.handlePaymentEvents(Collections.emptyMap(), ack);
        verify(ack).acknowledge();
    }

    @Test
    void handleBalanceEvents_EmptyMap_DoesNotThrow() {
        consumer.handleBalanceEvents(Collections.emptyMap(), ack);
        verify(ack).acknowledge();
    }

    // ── Rule: Map with null values must not crash the consumer ───────────────

    @Test
    void handleAccountEvents_NullValues_DoesNotThrow() {
        Map<String, Object> nullValues = new HashMap<>();
        nullValues.put("eventType", null);
        nullValues.put("eventId",   null);
        nullValues.put("userId",    null);

        consumer.handleAccountEvents(nullValues, ack);

        verify(notificationService).processEvent(nullValues);
        verify(ack).acknowledge();
    }

    @Test
    void handleTransactionEvents_NullEventType_UsesUnknownDefault() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", null);
        event.put("amount",    "5000");

        consumer.handleTransactionEvents(event, ack);

        verify(notificationService).processEvent(event);
        verify(ack).acknowledge();
    }

    // ── Rule: Non-String eventType must not crash the consumer ───────────────

    @Test
    void handleAccountEvents_IntegerEventType_DoesNotThrow() {
        Map<String, Object> event = Map.of("eventType", 42);  // wrong type

        consumer.handleAccountEvents(event, ack);

        verify(ack).acknowledge();
    }

    @Test
    void handleFraudEvents_BooleanEventType_DoesNotThrow() {
        Map<String, Object> event = Map.of("eventType", true);

        consumer.handleFraudEvents(event, ack);

        verify(ack).acknowledge();
    }

    // ── Rule: Unknown/unexpected eventType must not crash the consumer ────────

    @Test
    void handleUserEvents_UnknownEventType_ProcessesWithoutCrash() {
        Map<String, Object> event = Map.of(
                "eventType", "TOTALLY_UNKNOWN_EVENT_XYZ",
                "eventId",   "evt-unknown"
        );

        consumer.handleUserEvents(event, ack);

        verify(notificationService).processEvent(event);
        verify(ack).acknowledge();
    }

    // ── Rule: Very large payload must not cause OOM / crash ───────────────────

    @Test
    void handleAccountEvents_OversizedPayload_DoesNotCrash() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ACCOUNT_CREATED");
        // 10k-character remark string simulating oversized field
        event.put("remarks",   "X".repeat(10_000));

        consumer.handleAccountEvents(event, ack);

        verify(ack).acknowledge();
    }

    // ── Rule: SQL-injection-like strings in event fields must not crash ───────

    @Test
    void handleLoanEvents_SqlInjectionInEventType_ProcessesSafely() {
        Map<String, Object> event = Map.of(
                "eventType", "'; DROP TABLE notifications; --",
                "eventId",   "evt-sqli"
        );

        consumer.handleLoanEvents(event, ack);

        verify(notificationService).processEvent(event);
        verify(ack).acknowledge();
    }

    // ── Rule: On service failure, offset must NOT be committed ───────────────

    @Test
    void handleAccountEvents_ServiceThrows_DoesNotAcknowledge_RethrowsForDlq() {
        doThrow(new RuntimeException("MongoDB timeout"))
                .when(notificationService).processEvent(any());

        assertThatThrownBy(() ->
                consumer.handleAccountEvents(Map.of("eventType", "ACCOUNT_CREATED"), ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MongoDB timeout");

        verify(ack, never()).acknowledge();
    }

    @Test
    void handleTransactionEvents_ServiceThrowsNpe_DoesNotAcknowledge() {
        doThrow(new NullPointerException("Unexpected null in template"))
                .when(notificationService).processEvent(any());

        assertThatThrownBy(() ->
                consumer.handleTransactionEvents(Map.of("eventType", "TRANSACTION_COMPLETED"), ack))
                .isInstanceOf(NullPointerException.class);

        verify(ack, never()).acknowledge();
    }

    @Test
    void handleFraudEvents_ServiceThrows_DoesNotAcknowledge() {
        doThrow(new RuntimeException("Template render failure"))
                .when(notificationService).processEvent(any());

        assertThatThrownBy(() ->
                consumer.handleFraudEvents(Map.of("eventType", "FRAUD_ALERT_RAISED"), ack))
                .isInstanceOf(RuntimeException.class);

        verify(ack, never()).acknowledge();
    }

    @Test
    void handleCardEvents_ServiceThrows_DoesNotAcknowledge() {
        doThrow(new RuntimeException("SMTP error"))
                .when(notificationService).processEvent(any());

        assertThatThrownBy(() ->
                consumer.handleCardEvents(Map.of("eventType", "CARD_ISSUED"), ack))
                .isInstanceOf(RuntimeException.class);

        verify(ack, never()).acknowledge();
    }

    // ── Rule: Multiple events in sequence — one failure must not block others ─
    //    Each invocation is independent; one throw must not corrupt state.

    @Test
    void handleAccountEvents_FailureThenSuccess_BothBehaviourCorrect() {
        doThrow(new RuntimeException("transient error"))
                .doNothing()
                .when(notificationService).processEvent(any());

        Map<String, Object> event = Map.of("eventType", "ACCOUNT_CREATED");

        // First call: service throws → no ACK
        assertThatThrownBy(() -> consumer.handleAccountEvents(event, ack))
                .isInstanceOf(RuntimeException.class);
        verify(ack, never()).acknowledge();

        // Second call: service succeeds → ACK
        consumer.handleAccountEvents(event, ack);
        verify(ack, times(1)).acknowledge();
    }
}
