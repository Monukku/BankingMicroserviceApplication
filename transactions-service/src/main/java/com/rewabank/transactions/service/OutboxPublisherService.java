package com.rewabank.transactions.service;

import com.rewabank.transactions.entity.OutboxEvent;
import com.rewabank.transactions.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final OutboxEventRepository         outboxRepo;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepo.findPendingEvents();
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(),
                                event.getAggregateId(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                handleFailure(event, ex.getMessage());
                            } else {
                                event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                                event.setProcessedAt(LocalDateTime.now());
                                outboxRepo.save(event);
                            }
                        });
            } catch (Exception e) {
                handleFailure(event, e.getMessage());
            }
        }
    }

    private void handleFailure(OutboxEvent event, String error) {
        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastError(error);
        if (event.getRetryCount() >= 5) {
            event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            log.error("Outbox FAILED after 5 retries: {} error: {}",
                    event.getEventType(), error);
        }
        outboxRepo.save(event);
    }
}
