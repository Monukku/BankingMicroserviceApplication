package com.rewabank.audit.kafka;

import com.rewabank.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllEventConsumerTest {

    @Mock AuditService auditService;

    @InjectMocks AllEventConsumer consumer;

    private static final Map<String, Object> EVENT = Map.of(
            "eventId",   "evt-001",
            "eventType", "ACCOUNT_ACTIVATED"
    );

    @Test
    void consumeAll_success_delegatesToAuditServiceWithTopic() {
        consumer.consumeAll(EVENT, "bank.account.activated");

        verify(auditService).record(EVENT, "bank.account.activated");
    }

    @Test
    void consumeAll_differentTopic_passesCorrectTopic() {
        consumer.consumeAll(EVENT, "bank.transaction.completed");

        verify(auditService).record(EVENT, "bank.transaction.completed");
    }

    @Test
    void consumeAll_serviceThrows_rethrowsForKafkaRetry() {
        doThrow(new RuntimeException("DB down"))
                .when(auditService).record(any(), any());

        assertThatThrownBy(() -> consumer.consumeAll(EVENT, "bank.account.activated"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");
    }
}