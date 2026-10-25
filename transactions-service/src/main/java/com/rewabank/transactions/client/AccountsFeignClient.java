package com.rewabank.transactions.client;

import com.rewabank.transactions.config.AccountsFeignConfig;
import com.rewabank.transactions.dto.AccountDebitCreditRequest;
import com.rewabank.transactions.dto.AccountInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Sync call to Accounts MS for balance operations.
 * retries = 0 — never retry debit/credit.
 */
@FeignClient(
        name = "accounts-service",
        url = "${accounts.service.url:http://accounts-service.banking.svc.cluster.local:8081}",
        configuration = AccountsFeignConfig.class,
        fallbackFactory = AccountsFeignClientFallbackFactory.class
)
public interface AccountsFeignClient {

    @GetMapping("/api/v1/accounts/{accountId}")
    AccountInfoResponse getById(@PathVariable UUID accountId);

    @GetMapping("/api/v1/accounts/lookup")
    AccountInfoResponse lookupByAccountNumber(@RequestParam("accountNumber") String accountNumber);

    @PatchMapping("/api/v1/accounts/{accountId}/debit")
    void debitAccount(@PathVariable UUID accountId,
                      @RequestBody AccountDebitCreditRequest request);

    @PatchMapping("/api/v1/accounts/{accountId}/credit")
    void creditAccount(@PathVariable UUID accountId,
                       @RequestBody AccountDebitCreditRequest request);
}
