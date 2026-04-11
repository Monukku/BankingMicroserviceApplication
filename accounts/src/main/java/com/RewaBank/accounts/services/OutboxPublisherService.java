package com.rewabank.accounts.services;

import com.rewabank.accounts.entity.OutboxEvent;
import com.rewabank.accounts.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox publisher — polls PENDING events and publishes to Kafka.
 * Called by OutboxScheduler every 5 seconds.
 * Guarantees at-least-once delivery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final OutboxEventRepository        outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int MAX_RETRIES = 5;

    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingEvents();

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(),
                                event.getAggregateId(),
                                event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                handleFailure(event, ex.getMessage());
                            } else {
                                markPublished(event);
                                log.debug("Outbox event published: {} offset: {}",
                                        event.getEventType(),
                                        result.getRecordMetadata().offset());
                            }
                        });
            } catch (Exception e) {
                handleFailure(event, e.getMessage());
            }
        }
    }

    private void markPublished(OutboxEvent event) {
        event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
        event.setProcessedAt(LocalDateTime.now());
        outboxEventRepository.save(event);
    }

    private void handleFailure(OutboxEvent event, String error) {
        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastError(error);
        if (event.getRetryCount() >= MAX_RETRIES) {
            event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            log.error("Outbox event FAILED after {} retries: {} error: {}",
                    MAX_RETRIES, event.getEventType(), error);
        }
        outboxEventRepository.save(event);
    }
}
