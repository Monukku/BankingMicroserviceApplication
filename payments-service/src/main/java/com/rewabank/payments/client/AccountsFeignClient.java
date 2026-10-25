package com.rewabank.payments.client;

import com.rewabank.payments.client.dto.AccountResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "accounts-service", url = "${accounts.service.url}")
public interface AccountsFeignClient {

    @GetMapping("/api/v1/accounts/{accountId}")
    AccountResponse getById(@PathVariable UUID accountId);
}
