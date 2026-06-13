package com.rewabank.cards.client;

import com.rewabank.cards.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "fraud-ms", url = "${feign.fraud-ms.url:http://localhost:8094}", configuration = FeignConfig.class)
public interface FraudFeignClient {

    // GET /api/v1/fraud/score — returns { accountId, score, action, reason, correlationId }
    @GetMapping("/api/v1/fraud/score")
    Map<String, Object> getScore(
            @RequestParam UUID accountId,
            @RequestParam BigDecimal amount,
            @RequestParam String transactionType,
            @RequestParam String correlationId);
}

