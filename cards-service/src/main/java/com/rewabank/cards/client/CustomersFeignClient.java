package com.rewabank.cards.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "customers-ms", url = "${feign.customers-ms.url:http://localhost:8080}")
public interface CustomersFeignClient {

    @GetMapping("/api/v1/customers/{id}/kyc-status")
    Map<String, Object> getKycStatus(@PathVariable UUID id);
}

