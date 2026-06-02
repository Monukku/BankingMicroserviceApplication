package com.rewabank.transactions.scheduler;

import com.rewabank.transactions.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxPublisherService outboxPublisherService;

    @Scheduled(fixedDelay = 5000)
    public void publishOutboxEvents() {
        outboxPublisherService.publishPendingEvents();
    }
}
