package com.rewabank.payments.scheduler;

import com.rewabank.payments.entity.OutboxEvent;
import com.rewabank.payments.kafka.PaymentEventProducer;
import com.rewabank.payments.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventProducer  paymentEventProducer;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository
                .findTop50ByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : pending) {
            try {
                paymentEventProducer.publish(
                        event.getTopic(),
                        event.getAggregateId(),
                        event.getPayload());
                event.setPublished(true);
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Outbox publish failed for event [{}]: {}",
                        event.getId(), e.getMessage());
            }
        }
    }
}