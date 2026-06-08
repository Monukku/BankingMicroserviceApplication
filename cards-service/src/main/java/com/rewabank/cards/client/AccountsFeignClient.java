package com.rewabank.cards.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "accounts-ms", url = "${feign.accounts-ms.url:http://localhost:8089}")
public interface AccountsFeignClient {

    @GetMapping("/api/v1/accounts/{id}")
    Map<String, Object> getAccount(@PathVariable UUID id);

    @GetMapping("/api/v1/accounts/{id}/balance")
    Map<String, Object> getBalance(@PathVariable UUID id);

    @PostMapping("/api/v1/accounts/{id}/debit")
    Map<String, Object> debitAccount(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount,
            @RequestParam String description);

    @PostMapping("/api/v1/accounts/{id}/credit")
    Map<String, Object> creditAccount(
            @PathVariable UUID id,
            @RequestParam BigDecimal amount,
            @RequestParam String description);

    @GetMapping("/api/v1/accounts/{id}/available-balance")
    Map<String, Object> getAvailableBalance(@PathVariable UUID id);
}

