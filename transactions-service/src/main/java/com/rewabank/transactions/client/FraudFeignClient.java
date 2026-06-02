package com.rewabank.transactions.client;

import com.rewabank.transactions.config.FeignConfig;
import com.rewabank.transactions.dto.FraudScoreResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inline fraud check — SYNC call before every debit.
 * Must respond within 3 seconds.
 * Fallback = FLAG (never silently approve).
 * retries = 0 — never retry fraud check.
 */
@FeignClient(
        name = "fraud-service",
        url = "${fraud.service.url:http://fraud-service.banking.svc.cluster.local:8094}",
        configuration = FeignConfig.class,
        fallback = FraudFeignClientFallback.class
)
public interface FraudFeignClient {

    @GetMapping("/api/v1/fraud/score")
    FraudScoreResponse getFraudScore(
            @RequestParam UUID accountId,
            @RequestParam BigDecimal amount,
            @RequestParam String transactionType,
            @RequestParam String correlationId
    );
}
