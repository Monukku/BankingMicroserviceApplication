package com.rewabank.accounts.client;

import com.rewabank.accounts.dto.KycStatusResponse;
import com.rewabank.accounts.exception.AccountException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class CustomersFeignClientFallback implements CustomersFeignClient {

    @Override
    public KycStatusResponse getKycStatus(UUID customerId) {
        log.error("Customers MS unavailable for KYC check — customerId: {}", customerId);
        // Never silently approve — if KYC check fails, throw exception
        throw new AccountException("ACCT_KYC_001",
                "KYC verification service unavailable. Account cannot be activated.");
    }
}
