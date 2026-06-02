package com.rewabank.transactions.client;

import com.rewabank.transactions.dto.FraudScoreResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Slf4j
public class FraudFeignClientFallback implements FraudFeignClient {

    @Override
    public FraudScoreResponse getFraudScore(UUID accountId, BigDecimal amount,
                                            String transactionType,
                                            String correlationId) {
        // CRITICAL: Fraud MS down → FLAG, never approve silently
        log.error("Fraud MS unavailable for account: {} correlationId: {}",
                accountId, correlationId);
        return new FraudScoreResponse(
                accountId.toString(),
                75,         // high score = risky
                "FLAG",     // flag for manual review — never APPROVE
                "Fraud service unavailable — flagged for manual review"
        );
    }
}
