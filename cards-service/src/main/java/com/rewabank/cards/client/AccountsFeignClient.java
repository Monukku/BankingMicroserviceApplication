package com.rewabank.cards.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "accounts-ms", url = "${feign.accounts-ms.url:http://localhost:8081}")
public interface AccountsFeignClient {

    @GetMapping("/api/v1/accounts/{id}")
    Map<String, Object> getAccount(@PathVariable UUID id);
}

