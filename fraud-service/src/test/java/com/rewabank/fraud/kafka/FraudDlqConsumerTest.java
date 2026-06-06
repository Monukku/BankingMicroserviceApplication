package com.rewabank.fraud.kafka;

import com.rewabank.fraud.service.FraudAlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudDlqConsumerTest {

    @Mock  private FraudAlertService fraudAlertService;
    @InjectMocks private FraudDlqConsumer fraudDlqConsumer;

    private static final String TXN_DLQ    = "bank.transaction.completed.dlq";
    private static final String BALANCE_DLQ = "bank.balance.updated.dlq";

    // ── alert creation ────────────────────────────────────────────────────────

    @Test
    void handleDlqEvent_ShouldCreateBlockAlert_WhenEventHasAccountId() {
        UUID accountId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("accountId", accountId.toString());
        event.put("amount", "5000.00");

        fraudDlqConsumer.handleDlqEvent(event, BALANCE_DLQ);

        ArgumentCaptor<UUID> idCaptor     = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Integer> scoreCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String>  actionCaptor = ArgumentCaptor.forClass(String.class);

        verify(fraudAlertService).createAlert(
                idCaptor.capture(), any(), any(), any(BigDecimal.class),
                scoreCaptor.capture(), actionCaptor.capture(), any());

        assertEquals(accountId, idCaptor.getValue());
        assertEquals(99, scoreCaptor.getValue());
        assertEquals("BLOCK", actionCaptor.getValue());
    }

    @Test
    void handleDlqEvent_ShouldFallbackToSourceAccountId_ForTransactionEvents() {
        UUID accountId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("sourceAccountId", accountId.toString());
        event.put("amount", "1000.00");

        fraudDlqConsumer.handleDlqEvent(event, TXN_DLQ);

        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(fraudAlertService).createAlert(
                captor.capture(), any(), any(), any(), eq(99), eq("BLOCK"), any());
        assertEquals(accountId, captor.getValue());
    }

    @Test
    void handleDlqEvent_ShouldIncludeTopicName_InTriggeredRules() {
        Map<String, Object> event = new HashMap<>();
        event.put("accountId", UUID.randomUUID().toString());
        event.put("amount", "500.00");

        fraudDlqConsumer.handleDlqEvent(event, TXN_DLQ);

        ArgumentCaptor<String> rulesCaptor = ArgumentCaptor.forClass(String.class);
        verify(fraudAlertService).createAlert(
                any(), any(), any(), any(), anyInt(), any(), rulesCaptor.capture());
        assertTrue(rulesCaptor.getValue().contains(TXN_DLQ));
    }

    @Test
    void handleDlqEvent_ShouldDefaultAmountToZero_WhenAmountMissing() {
        Map<String, Object> event = new HashMap<>();
        event.put("accountId", UUID.randomUUID().toString());

        fraudDlqConsumer.handleDlqEvent(event, BALANCE_DLQ);

        ArgumentCaptor<BigDecimal> amtCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(fraudAlertService).createAlert(
                any(), any(), any(), amtCaptor.capture(), anyInt(), any(), any());
        assertEquals(0, BigDecimal.ZERO.compareTo(amtCaptor.getValue()));
    }

    // ── no-op when no account id ──────────────────────────────────────────────

    @Test
    void handleDlqEvent_ShouldNotCreateAlert_WhenNoAccountIdPresent() {
        Map<String, Object> event = Map.of("amount", "5000.00");

        fraudDlqConsumer.handleDlqEvent(event, TXN_DLQ);

        verify(fraudAlertService, never()).createAlert(
                any(), any(), any(), any(), anyInt(), any(), any());
    }

    // ── resilience ────────────────────────────────────────────────────────────

    @Test
    void handleDlqEvent_ShouldNotPropagate_WhenCreateAlertThrows() {
        Map<String, Object> event = new HashMap<>();
        event.put("accountId", UUID.randomUUID().toString());
        event.put("amount", "5000.00");

        doThrow(new RuntimeException("DB down"))
                .when(fraudAlertService)
                .createAlert(any(), any(), any(), any(), anyInt(), any(), any());

        assertDoesNotThrow(() -> fraudDlqConsumer.handleDlqEvent(event, BALANCE_DLQ));
    }
}
