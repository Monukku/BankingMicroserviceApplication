package com.rewabank.loans.service;

import com.rewabank.loans.entity.OutboxEvent;
import com.rewabank.loans.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final OutboxEventRepository         outboxRepo;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int  MAX_RETRIES       = 5;
    private static final long BASE_DELAY_SECONDS = 5L;
    private static final long MAX_DELAY_SECONDS  = 300L;

    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepo.findPendingEvents(LocalDateTime.now());
        for (OutboxEvent event : pending) {
            try {
                var result = kafkaTemplate.send(event.getTopic(),
                        event.getAggregateId(), event.getPayload()).get();

                event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                event.setProcessedAt(LocalDateTime.now());
                outboxRepo.save(event);

                log.debug("Outbox event published: {} offset: {}",
                        event.getEventType(), result.getRecordMetadata().offset());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handleFailure(event, "Interrupted: " + e.getMessage());
            } catch (ExecutionException e) {
                handleFailure(event, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            } catch (Exception e) {
                handleFailure(event, e.getMessage());
            }
        }
    }

    private void handleFailure(OutboxEvent event, String error) {
        int retries = event.getRetryCount() + 1;
        event.setRetryCount(retries);
        event.setLastError(error);

        if (retries >= MAX_RETRIES) {
            event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            log.error("Loans outbox event FAILED after {} retries: {} error: {}",
                    MAX_RETRIES, event.getEventType(), error);
        } else {
            long delaySecs = Math.min(
                    (long) Math.pow(2, retries) * BASE_DELAY_SECONDS,
                    MAX_DELAY_SECONDS);
            event.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySecs));
            log.warn("Loans outbox retry {}/{} in {}s: {} error: {}",
                    retries, MAX_RETRIES - 1, delaySecs, event.getEventType(), error);
        }
        outboxRepo.save(event);
    }
}