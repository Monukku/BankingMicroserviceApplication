package com.rewabank.transactions.service;

import com.rewabank.transactions.entity.OutboxEvent;
import com.rewabank.transactions.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock OutboxEventRepository         outboxRepo;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks OutboxPublisherService outboxPublisherService;

    private OutboxEvent pendingEvent(int retryCount) {
        return OutboxEvent.builder()
                .aggregateId("txn-1")
                .eventType("TRANSACTION_COMPLETED")
                .topic("bank.transaction.completed")
                .payload("{\"txn\":\"data\"}")
                .retryCount(retryCount)
                .build();
    }

    @Test
    void publishPendingEvents_emptyList_doesNotCallKafka() {
        when(outboxRepo.findPendingEvents()).thenReturn(List.of());
        outboxPublisherService.publishPendingEvents();
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPendingEvents_success_setsStatusPublishedAndSaves() {
        OutboxEvent event = pendingEvent(0);
        when(outboxRepo.findPendingEvents()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxPublisherService.publishPendingEvents();

        // Kills VoidMethodCall on event.setStatus() and outboxRepo.save()
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PUBLISHED);
        verify(outboxRepo).save(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPendingEvents_kafkaFails_incrementsRetryAndSaves() {
        OutboxEvent event = pendingEvent(0);
        when(outboxRepo.findPendingEvents()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka down")));

        outboxPublisherService.publishPendingEvents();

        // Kills VoidMethodCall on setRetryCount + outboxRepo.save() in handleFailure
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("Kafka down");
        verify(outboxRepo).save(event);
        // Not yet FAILED — only 1 retry
        assertThat(event.getStatus()).isNotEqualTo(OutboxEvent.OutboxStatus.FAILED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPendingEvents_atRetryLimit_marksAsFailed() {
        // Kills ConditionalsBoundary on: retryCount >= 5
        OutboxEvent event = pendingEvent(4);   // will become 5 after increment → FAILED
        when(outboxRepo.findPendingEvents()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));

        outboxPublisherService.publishPendingEvents();

        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.FAILED);
        verify(outboxRepo).save(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPendingEvents_belowRetryLimit_doesNotMarkFailed() {
        // Kills ConditionalsBoundary: retryCount = 3 → becomes 4, NOT FAILED
        OutboxEvent event = pendingEvent(3);
        when(outboxRepo.findPendingEvents()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("err")));

        outboxPublisherService.publishPendingEvents();

        assertThat(event.getRetryCount()).isEqualTo(4);
        assertThat(event.getStatus()).isNotEqualTo(OutboxEvent.OutboxStatus.FAILED);
    }
}