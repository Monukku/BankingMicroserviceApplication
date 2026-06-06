package com.rewabank.accounts.scheduler;

import com.rewabank.accounts.repository.AccountsRepository;
import com.rewabank.accounts.services.AccountService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DormancyScheduler {

    private final AccountsRepository accountRepository;
    private final AccountService     accountService;
    private final MeterRegistry      meterRegistry;

    // Runs every day at 2:00 AM — detect dormant accounts
    // Account is dormant if no transaction in 12 months (RBI guideline)
    @Scheduled(cron = "0 0 2 * * ?")
    public void detectDormantAccounts() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusMonths(12);
            var dormantCandidates = accountRepository.findAccountsForDormancy(cutoff);

            log.info("Dormancy check — found {} candidate accounts", dormantCandidates.size());

            dormantCandidates.forEach(account -> {
                try {
                    accountService.markDormant(account.getId());
                } catch (Exception e) {
                    log.error("Failed to mark dormant for account {}: {}",
                            account.getAccountNumber(), e.getMessage());
                }
            });
        } finally {
            sample.stop(Timer.builder("scheduler.dormancy.duration")
                    .description("Time taken to run dormancy detection")
                    .register(meterRegistry));
        }
    }
}
