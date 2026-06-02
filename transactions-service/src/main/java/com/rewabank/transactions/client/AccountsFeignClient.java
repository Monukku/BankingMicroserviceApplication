package com.rewabank.transactions.client;

import com.rewabank.transactions.config.FeignConfig;
import com.rewabank.transactions.dto.AccountDebitCreditRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * Sync call to Accounts MS for balance operations.
 * retries = 0 — never retry debit/credit.
 */
@FeignClient(
        name = "accounts-service",
        url = "${accounts.service.url:http://accounts-service.banking.svc.cluster.local:8081}",
        configuration = FeignConfig.class,
        fallback = AccountsFeignClientFallback.class
)
public interface AccountsFeignClient {

    @PatchMapping("/api/v1/accounts/{accountId}/debit")
    void debitAccount(@PathVariable UUID accountId,
                      @RequestBody AccountDebitCreditRequest request);

    @PatchMapping("/api/v1/accounts/{accountId}/credit")
    void creditAccount(@PathVariable UUID accountId,
                       @RequestBody AccountDebitCreditRequest request);
}
