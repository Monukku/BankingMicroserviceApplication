package com.rewabank.accounts.client;

import com.rewabank.accounts.dto.KycStatusResponse;
import com.rewabank.accounts.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

// Sync KYC gate call — Accounts MS must know KYC status before activation
// URL uses K8s DNS — mTLS handled by Istio sidecar transparently
@FeignClient(
        name = "customers-service",
        url = "${customers.service.url:http://customers-service.banking.svc.cluster.local:8080}",
        configuration = FeignConfig.class,
        fallback = CustomersFeignClientFallback.class
)
public interface CustomersFeignClient {

    @GetMapping("/api/v1/customers/{customerId}/kyc-status")
    KycStatusResponse getKycStatus(@PathVariable UUID customerId);
}
