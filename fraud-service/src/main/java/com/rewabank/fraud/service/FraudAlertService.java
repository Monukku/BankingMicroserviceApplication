package com.rewabank.fraud.service;

import com.rewabank.fraud.kafka.FraudEventProducer;
import com.rewabank.fraud.model.FraudAlert;
import com.rewabank.fraud.repository.FraudAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudAlertService {

    private final FraudAlertRepository fraudAlertRepository;
    private final FraudEventProducer   fraudEventProducer;

    @Transactional
    public FraudAlert createAlert(UUID accountId, String keycloakUserId,
                                  String transactionId, BigDecimal amount,
                                  int score, String action,
                                  String triggeredRules) {
        FraudAlert alert = FraudAlert.builder()
                .accountId(accountId)
                .keycloakUserId(keycloakUserId)
                .transactionId(transactionId)
                .amount(amount)
                .fraudScore(score)
                .action(action)
                .triggeredRules(triggeredRules)
                .status(FraudAlert.AlertStatus.OPEN)
                .build();

        fraudAlertRepository.save(alert);

        // Publish async — Cards MS will auto-block on BLOCK action
        fraudEventProducer.publishAlertRaised(
                alert.getId().toString(),
                accountId.toString(),
                keycloakUserId,
                transactionId,
                amount.toString(),
                score,
                action,
                triggeredRules
        );

        log.warn("Fraud alert created: {} account: {} score: {} action: {}",
                alert.getId(), accountId, score, action);

        return alert;
    }

    @Transactional
    public FraudAlert resolveAlert(UUID alertId, String resolvedBy,
                                   String notes, String resolution) {
        FraudAlert alert = fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));

        FraudAlert.AlertStatus newStatus = "FALSE_POSITIVE".equals(resolution)
                ? FraudAlert.AlertStatus.FALSE_POSITIVE
                : FraudAlert.AlertStatus.RESOLVED;

        alert.setStatus(newStatus);
        alert.setResolvedBy(resolvedBy);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolutionNotes(notes);
        fraudAlertRepository.save(alert);

        // Publish cleared event — Cards MS can unblock if needed
        fraudEventProducer.publishAlertCleared(
                alertId.toString(),
                alert.getAccountId().toString(),
                resolution
        );

        log.info("Fraud alert resolved: {} by: {} resolution: {}",
                alertId, resolvedBy, resolution);
        return alert;
    }

    public Page<FraudAlert> getOpenAlerts(Pageable pageable) {
        return fraudAlertRepository.findByStatusOrderByCreatedAtDesc(
                FraudAlert.AlertStatus.OPEN, pageable);
    }

    public Page<FraudAlert> getByAccount(UUID accountId, Pageable pageable) {
        return fraudAlertRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }
}
