package com.rewabank.loans.client;

import com.rewabank.loans.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "fraud-service",
        url = "${fraud.service.url:http://fraud-service.banking.svc.cluster.local:8094}",
        configuration = FeignConfig.class,
        fallback = FraudFeignClientFallback.class
)
public interface FraudFeignClient {

    @GetMapping("/api/v1/fraud/score")
    Map<String, Object> getScore(
            @RequestParam UUID accountId,
            @RequestParam BigDecimal amount,
            @RequestParam String transactionType,
            @RequestParam String correlationId);
}