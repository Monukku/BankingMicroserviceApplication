package com.rewabank.accounts.scheduler;

import com.rewabank.accounts.repository.OutboxEventRepository;
import com.rewabank.accounts.services.OutboxPublisherService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxPublisherService outboxPublisherService;
    private final OutboxEventRepository  outboxEventRepository;
    private final MeterRegistry          meterRegistry;

    @Scheduled(fixedDelayString = "${outbox.publish.interval-ms:10000}")
    public void publishOutboxEvents() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            outboxPublisherService.publishPendingEvents();
        } finally {
            sample.stop(Timer.builder("scheduler.outbox.duration")
                    .tag("service", "accounts")
                    .description("Time taken to publish pending outbox events")
                    .register(meterRegistry));
        }
    }

    // Purge old processed rows daily at 3:15 AM — prevents unbounded table growth
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeOutboxEvents() {
        int published = outboxEventRepository.deletePublished(LocalDateTime.now().minusDays(7));
        int failed    = outboxEventRepository.deleteFailed(LocalDateTime.now().minusDays(30));
        log.info("Outbox purge: deleted {} published (>7d) and {} failed (>30d) events", published, failed);
    }
}
