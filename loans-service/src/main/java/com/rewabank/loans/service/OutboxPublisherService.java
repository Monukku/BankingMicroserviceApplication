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
                // FIXED: use get() for synchronous result — async whenComplete
                // inside @Transactional is unreliable (transaction commits before
                // callback fires, leaving event in PENDING state permanently)
                kafkaTemplate.send(event.getTopic(),
                        event.getAggregateId(), event.getPayload()).get();

                event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                event.setProcessedAt(LocalDateTime.now());
                outboxRepo.save(event);

                log.debug("Outbox event published: {} topic: {}",
                        event.getEventType(), event.getTopic());

            } catch (Exception e) {
                log.error("Outbox publish failed for event {}: {}",
                        event.getId(), e.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getMessage());
                if (event.getRetryCount() >= 5) {
                    event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                    log.error("Outbox event {} permanently failed after 5 retries",
                            event.getId());
                }
                outboxRepo.save(event);
            }
        }
    }
}