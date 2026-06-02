package com.rewabank.loans.client;

import com.rewabank.loans.config.FeignConfig;
import com.rewabank.loans.dto.AccountCreditRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PatchMapping;
import java.util.UUID;

@FeignClient(
        name = "accounts-service",
        url = "${accounts.service.url:http://accounts-service.banking.svc.cluster.local:8081}",
        configuration = FeignConfig.class,
        fallback = AccountsFeignClientFallback.class
)
public interface AccountsFeignClient {

    @PatchMapping("/api/v1/accounts/{accountId}/credit")
    void creditAccount(@PathVariable UUID accountId,
                       @RequestBody AccountCreditRequest request);
}