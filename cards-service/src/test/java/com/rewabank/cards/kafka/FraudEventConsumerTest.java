package com.rewabank.cards.kafka;

import com.rewabank.cards.service.CardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudEventConsumerTest {

    @Mock CardService cardService;
    @InjectMocks FraudEventConsumer consumer;

    @Test
    void handleFraudAlert_nullAccountId_doesNothing() {
        // Kills NegateConditionals on: if (accountId == null) return
        consumer.handleFraudAlert(Map.of("action", "BLOCK"));
        verify(cardService, never()).autoBlockByAccountId(any(), anyString());
    }

    @Test
    void handleFraudAlert_blockAction_callsAutoBlock() {
        // Kills NegateConditionals on: "BLOCK".equals(action)
        // Kills VoidMethodCall on: cardService.autoBlockByAccountId(...)
        UUID accountId = UUID.randomUUID();
        consumer.handleFraudAlert(Map.of(
                "accountId", accountId.toString(),
                "action", "BLOCK",
                "reason", "Suspicious activity"));
        verify(cardService).autoBlockByAccountId(accountId, "Suspicious activity");
    }

    @Test
    void handleFraudAlert_flagAction_doesNotCallAutoBlock() {
        UUID accountId = UUID.randomUUID();
        consumer.handleFraudAlert(Map.of(
                "accountId", accountId.toString(),
                "action", "FLAG"));
        verify(cardService, never()).autoBlockByAccountId(any(), anyString());
    }

    @Test
    void handleFraudAlert_defaultAction_doesNotCallAutoBlock() {
        UUID accountId = UUID.randomUUID();
        // No action key → defaults to "FLAG"
        Map<String, Object> event = new HashMap<>();
        event.put("accountId", accountId.toString());
        consumer.handleFraudAlert(event);
        verify(cardService, never()).autoBlockByAccountId(any(), anyString());
    }

    @Test
    void handleFraudAlert_blockAction_exceptionIsRethrown() {
        UUID accountId = UUID.randomUUID();
        doThrow(new RuntimeException("DB error"))
                .when(cardService).autoBlockByAccountId(any(), anyString());

        assertThatThrownBy(() -> consumer.handleFraudAlert(Map.of(
                "accountId", accountId.toString(),
                "action", "BLOCK",
                "reason", "test")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");
    }
}
