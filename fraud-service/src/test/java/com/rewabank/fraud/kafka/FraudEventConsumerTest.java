package com.rewabank.fraud.kafka;

import com.rewabank.fraud.service.FraudAlertService;
import com.rewabank.fraud.service.FraudScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudEventConsumerTest {

    @Mock FraudScoringService fraudScoringService;
    @Mock FraudAlertService   fraudAlertService;
    @InjectMocks FraudEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "alertThreshold", 80);
    }

    private FraudScoringService.ScoringResult result(int score, String action) {
        return new FraudScoringService.ScoringResult(score, action, "VELOCITY_CHECK");
    }

    // ── handleTransactionCompleted ────────────────────────────────────────────

    @Test
    void handleTransactionCompleted_nullAccountId_returnsEarly() {
        // Kills NegateConditionals on: if (accountId == null) return
        consumer.handleTransactionCompleted(Map.of("amount", "1000"));
        verify(fraudScoringService, never()).score(any(), any(), any(), any());
    }

    @Test
    void handleTransactionCompleted_blockActionAboveThreshold_createsAlert() {
        // Kills NegateConditionals on "BLOCK".equals(action), score >= threshold
        UUID accountId = UUID.randomUUID();
        when(fraudScoringService.score(any(), any(), any(), any()))
                .thenReturn(result(90, "BLOCK"));

        consumer.handleTransactionCompleted(Map.of(
                "sourceAccountId", accountId.toString(),
                "amount", "5000",
                "transactionId", "txn-001"));

        verify(fraudAlertService).createAlert(
                eq(accountId), any(), eq("txn-001"),
                any(BigDecimal.class), eq(90), eq("BLOCK"), anyString());
    }

    @Test
    void handleTransactionCompleted_flagActionAboveThreshold_createsAlert() {
        // Kills NegateConditionals on "FLAG".equals(action)
        UUID accountId = UUID.randomUUID();
        when(fraudScoringService.score(any(), any(), any(), any()))
                .thenReturn(result(85, "FLAG"));

        consumer.handleTransactionCompleted(Map.of(
                "sourceAccountId", accountId.toString(),
                "amount", "3000"));

        verify(fraudAlertService).createAlert(any(), any(), any(),
                any(BigDecimal.class), eq(85), eq("FLAG"), anyString());
    }

    @Test
    void handleTransactionCompleted_scoreBelowThreshold_doesNotCreateAlert() {
        // Kills ConditionalsBoundary on: score >= alertThreshold
        UUID accountId = UUID.randomUUID();
        when(fraudScoringService.score(any(), any(), any(), any()))
                .thenReturn(result(79, "FLAG"));

        consumer.handleTransactionCompleted(Map.of(
                "sourceAccountId", accountId.toString(),
                "amount", "100"));

        verify(fraudAlertService, never()).createAlert(any(), any(), any(),
                any(), anyInt(), any(), any());
    }

    @Test
    void handleTransactionCompleted_allowAction_doesNotCreateAlert() {
        UUID accountId = UUID.randomUUID();
        when(fraudScoringService.score(any(), any(), any(), any()))
                .thenReturn(result(50, "ALLOW"));

        consumer.handleTransactionCompleted(Map.of(
                "sourceAccountId", accountId.toString(),
                "amount", "200"));

        verify(fraudAlertService, never()).createAlert(any(), any(), any(),
                any(), anyInt(), any(), any());
    }

    // ── handleBalanceUpdated ──────────────────────────────────────────────────

    @Test
    void handleBalanceUpdated_nonDebit_returnsEarly() {
        // Kills NegateConditionals on: !"DEBIT".equals(direction)
        consumer.handleBalanceUpdated(Map.of(
                "direction", "CREDIT",
                "accountId", UUID.randomUUID().toString()));
        verify(fraudAlertService, never()).createAlert(any(), any(), any(),
                any(), anyInt(), any(), any());
    }

    @Test
    void handleBalanceUpdated_nullAccountId_returnsEarly() {
        Map<String, Object> event = new HashMap<>();
        event.put("direction", "DEBIT");
        consumer.handleBalanceUpdated(event);
        verify(fraudAlertService, never()).createAlert(any(), any(), any(),
                any(), anyInt(), any(), any());
    }

    @Test
    void handleBalanceUpdated_largeBalanceDrop_createsAlert() {
        // Kills ConditionalsBoundary on: dropPercent >= 80
        UUID accountId = UUID.randomUUID();
        consumer.handleBalanceUpdated(Map.of(
                "direction",       "DEBIT",
                "accountId",       accountId.toString(),
                "previousBalance", "10000",
                "newBalance",      "500"   // 95% drop
        ));
        verify(fraudAlertService).createAlert(
                eq(accountId), any(), isNull(),
                any(BigDecimal.class), eq(85), eq("FLAG"), eq("LARGE_BALANCE_DROP(+85)"));
    }

    @Test
    void handleBalanceUpdated_smallBalanceDrop_doesNotCreateAlert() {
        UUID accountId = UUID.randomUUID();
        consumer.handleBalanceUpdated(Map.of(
                "direction",       "DEBIT",
                "accountId",       accountId.toString(),
                "previousBalance", "10000",
                "newBalance",      "9000"  // 10% drop — below threshold
        ));
        verify(fraudAlertService, never()).createAlert(any(), any(), any(),
                any(), anyInt(), any(), any());
    }

    @Test
    void handleBalanceUpdated_zeroPreviousBalance_doesNotCreateAlert() {
        // Kills NegateConditionals on: prev.compareTo(BigDecimal.ZERO) > 0
        UUID accountId = UUID.randomUUID();
        consumer.handleBalanceUpdated(Map.of(
                "direction",       "DEBIT",
                "accountId",       accountId.toString(),
                "previousBalance", "0",
                "newBalance",      "0"
        ));
        verify(fraudAlertService, never()).createAlert(any(), any(), any(),
                any(), anyInt(), any(), any());
    }

    // ── handleAccountCreated ──────────────────────────────────────────────────

    @Test
    void handleAccountCreated_nonNullAccountId_callsMarkNewAccount() {
        // Kills NegateConditionals + VoidMethodCall
        UUID accountId = UUID.randomUUID();
        consumer.handleAccountCreated(Map.of("accountId", accountId.toString()));
        verify(fraudScoringService).markNewAccount(accountId);
    }

    @Test
    void handleAccountCreated_nullAccountId_doesNothing() {
        Map<String, Object> event = new HashMap<>();
        consumer.handleAccountCreated(event);
        verify(fraudScoringService, never()).markNewAccount(any());
    }
}