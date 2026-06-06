package com.rewabank.loans.scheduler;

import com.rewabank.loans.service.OutboxPublisherService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxPublisherService outboxPublisherService;
    private final MeterRegistry          meterRegistry;

    @Scheduled(fixedDelayString = "${outbox.publish.interval-ms:10000}")
    public void publishOutboxEvents() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            outboxPublisherService.publishPendingEvents();
        } finally {
            sample.stop(Timer.builder("scheduler.outbox.duration")
                    .tag("service", "loans")
                    .description("Time taken to publish pending outbox events")
                    .register(meterRegistry));
        }
    }
}
