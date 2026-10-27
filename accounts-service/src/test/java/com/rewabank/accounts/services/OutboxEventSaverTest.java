package com.rewabank.accounts.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.accounts.entity.OutboxEvent;
import com.rewabank.accounts.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventSaverTest {

    @Mock OutboxEventRepository outboxEventRepository;
    @Mock ObjectMapper           objectMapper;
    @InjectMocks OutboxEventSaver outboxEventSaver;

    @Test
    void save_success_persistsOutboxEvent() throws Exception {
        // Kills VoidMethodCall on outboxEventRepository.save()
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"key\":\"val\"}");

        outboxEventSaver.save("acct-123", "ACCOUNT_CREATED",
                "bank.account.created", Map.of("accountId", "acct-123"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateId()).isEqualTo("acct-123");
        assertThat(saved.getEventType()).isEqualTo("ACCOUNT_CREATED");
        assertThat(saved.getTopic()).isEqualTo("bank.account.created");
        assertThat(saved.getPayload()).isEqualTo("{\"key\":\"val\"}");
    }

    @Test
    void save_jsonSerializationFails_throwsIllegalStateException() throws Exception {
        // Kills NullReturn / VoidMethodCall in the catch path
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("bad json") {});

        assertThatThrownBy(() -> outboxEventSaver.save(
                "acct-456", "ACCOUNT_FROZEN", "bank.account.frozen", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Outbox serialization failed");

        verify(outboxEventRepository, never()).save(any());
    }
}