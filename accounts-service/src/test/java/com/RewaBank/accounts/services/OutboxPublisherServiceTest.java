package com.rewabank.accounts.services;

import com.rewabank.accounts.entity.OutboxEvent;
import com.rewabank.accounts.repository.OutboxEventRepository;
import com.rewabank.accounts.services.OutboxPublisherService;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class OutboxPublisherServiceTest {

    @Mock private OutboxEventRepository         outboxEventRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks private OutboxPublisherService outboxPublisherService;

    private OutboxEvent pendingEvent;

    @BeforeEach
    void setUp() {
        pendingEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId("acct-001")
                .eventType("ACCOUNT_CREATED")
                .topic("bank.account.created")
                .payload("{}")
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .build();

        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    // ── success path ──────────────────────────────────────────────────────────

    @Test
    void publishPendingEvents_ShouldMarkPublished_WhenKafkaSendSucceeds() {
        SendResult<String, Object> sendResult = mockSendResult();
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        when(outboxEventRepository.findPendingEvents(any(LocalDateTime.class)))
                .thenReturn(List.of(pendingEvent));

        outboxPublisherService.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertEquals(OutboxEvent.OutboxStatus.PUBLISHED, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getProcessedAt());
    }

    // ── backoff scheduling ────────────────────────────────────────────────────

    @Test
    void publishPendingEvents_ShouldSetNextRetryAt_WhenKafkaSendFails() {
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);
        when(outboxEventRepository.findPendingEvents(any(LocalDateTime.class)))
                .thenReturn(List.of(pendingEvent));

        LocalDateTime before = LocalDateTime.now();
        outboxPublisherService.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertEquals(1, saved.getRetryCount());
        assertEquals(OutboxEvent.OutboxStatus.PENDING, saved.getStatus());
        assertNotNull(saved.getNextRetryAt());
        // retry 1: 2^1 * 5 = 10 seconds
        assertTrue(saved.getNextRetryAt().isAfter(before.plusSeconds(9)));
        assertTrue(saved.getNextRetryAt().isBefore(before.plusSeconds(11)));
    }

    @Test
    void publishPendingEvents_BackoffShouldDouble_OnSecondFailure() {
        pendingEvent.setRetryCount(1); // already failed once
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);
        when(outboxEventRepository.findPendingEvents(any(LocalDateTime.class)))
                .thenReturn(List.of(pendingEvent));

        LocalDateTime before = LocalDateTime.now();
        outboxPublisherService.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertEquals(2, saved.getRetryCount());
        // retry 2: 2^2 * 5 = 20 seconds
        assertTrue(saved.getNextRetryAt().isAfter(before.plusSeconds(19)));
        assertTrue(saved.getNextRetryAt().isBefore(before.plusSeconds(21)));
    }

    @Test
    void publishPendingEvents_ShouldScheduleMaxReachableDelay_OnFourthRetry() {
        pendingEvent.setRetryCount(3); // retry 4 = 2^4 * 5 = 80s (highest before FAILED)
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);
        when(outboxEventRepository.findPendingEvents(any(LocalDateTime.class)))
                .thenReturn(List.of(pendingEvent));

        LocalDateTime before = LocalDateTime.now();
        outboxPublisherService.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals(4, saved.getRetryCount());
        assertEquals(OutboxEvent.OutboxStatus.PENDING, saved.getStatus());
        // delay = 2^4 * 5 = 80s
        assertTrue(saved.getNextRetryAt().isAfter(before.plusSeconds(79)));
        assertTrue(saved.getNextRetryAt().isBefore(before.plusSeconds(81)));
    }

    // ── max retries → FAILED ──────────────────────────────────────────────────

    @Test
    void publishPendingEvents_ShouldMarkFailed_WhenMaxRetriesReached() {
        pendingEvent.setRetryCount(4); // one more failure = 5 = MAX_RETRIES
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);
        when(outboxEventRepository.findPendingEvents(any(LocalDateTime.class)))
                .thenReturn(List.of(pendingEvent));

        outboxPublisherService.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertEquals(OutboxEvent.OutboxStatus.FAILED, saved.getStatus());
        assertEquals(5, saved.getRetryCount());
        assertNull(saved.getNextRetryAt()); // no point scheduling a retry
    }

    // ── empty batch ───────────────────────────────────────────────────────────

    @Test
    void publishPendingEvents_ShouldDoNothing_WhenNoPendingEvents() {
        when(outboxEventRepository.findPendingEvents(any(LocalDateTime.class)))
                .thenReturn(List.of());

        outboxPublisherService.publishPendingEvents();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(outboxEventRepository, never()).save(any());
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private SendResult<String, Object> mockSendResult() {
        RecordMetadata meta = new RecordMetadata(
                new TopicPartition("bank.account.created", 0), 0, 0, 0, 0, 0);
        SendResult<String, Object> result = mock(SendResult.class);
        when(result.getRecordMetadata()).thenReturn(meta);
        return result;
    }
}
