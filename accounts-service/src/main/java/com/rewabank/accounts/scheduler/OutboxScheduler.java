package com.rewabank.accounts.scheduler;

import com.rewabank.accounts.services.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxPublisherService outboxPublisherService;

    // Poll every 5 seconds — publish pending outbox events to Kafka
    @Scheduled(fixedDelay = 5000)
    public void publishOutboxEvents() {
        outboxPublisherService.publishPendingEvents();
    }
}
