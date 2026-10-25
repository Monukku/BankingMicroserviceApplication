package com.rewabank.accounts.scheduler;

import com.rewabank.accounts.repository.AccountsRepository;
import com.rewabank.accounts.services.DepositService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Daily scheduler — runs at 01:00 IST.
 * Processes:
 *   1. FD maturity payouts
 *   2. RD monthly installment debits
 *
 * Each account is processed in its own transaction (@Transactional on DepositService methods)
 * so a failure for one FD/RD does not roll back others.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DepositScheduler {

    private final AccountsRepository accountsRepository;
    private final DepositService     depositService;

    // 01:00 IST daily = 19:30 UTC previous day
    @Scheduled(cron = "0 30 19 * * *", zone = "UTC")
    public void processMaturities() {
        LocalDate today = LocalDate.now();
        log.info("DepositScheduler: checking FD maturities for date {}", today);

        var maturedFds = accountsRepository.findMaturedFixedDeposits(today);
        log.info("Found {} FD(s) due for maturity", maturedFds.size());

        for (var fd : maturedFds) {
            try {
                depositService.processFdMaturity(fd.getId());
            } catch (Exception e) {
                log.error("Failed to process FD maturity for account {} — skipping: {}",
                        fd.getAccountNumber(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 20 * * *", zone = "UTC")  // 01:30 IST daily
    public void processRdInstallments() {
        LocalDate today = LocalDate.now();
        log.info("DepositScheduler: checking RD installments for date {}", today);

        var dueRds = accountsRepository.findDueRecurringDeposits(today);
        log.info("Found {} RD(s) due for installment debit", dueRds.size());

        for (var rd : dueRds) {
            try {
                depositService.processRdInstallment(rd.getId());
            } catch (Exception e) {
                log.error("Failed to process RD installment for account {} — skipping: {}",
                        rd.getAccountNumber(), e.getMessage());
            }
        }
    }
}
