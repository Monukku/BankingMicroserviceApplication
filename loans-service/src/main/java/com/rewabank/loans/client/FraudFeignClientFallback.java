package com.rewabank.loans.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class FraudFeignClientFallback implements FraudFeignClient {

    @Override
    public Map<String, Object> getScore(UUID accountId, BigDecimal amount,
                                        String transactionType, String correlationId) {
        log.warn("Fraud service unavailable for loan application — accountId: {}. Proceeding with score=0/ALLOW for human review.", accountId);
        return Map.of(
                "accountId",     accountId.toString(),
                "score",         0,
                "action",        "ALLOW",
                "reason",        "Fraud service unavailable — manual review required",
                "correlationId", correlationId
        );
    }
}