package com.rewabank.accounts.client;

import com.rewabank.accounts.exception.AccountException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomersFeignClientFallbackTest {

    private final CustomersFeignClientFallback fallback = new CustomersFeignClientFallback();

    @Test
    void getKycStatus_alwaysThrowsAccountException() {
        assertThatThrownBy(() -> fallback.getKycStatus(UUID.randomUUID()))
                .isInstanceOf(AccountException.class)
                .hasMessageContaining("KYC verification service unavailable");
    }

    @Test
    void getKycStatus_exceptionHasCorrectErrorCode() {
        try {
            fallback.getKycStatus(UUID.randomUUID());
        } catch (AccountException ex) {
            // Kills NullReturn on the thrown exception's errorCode
            org.assertj.core.api.Assertions.assertThat(ex.getErrorCode())
                    .isEqualTo("ACCT_KYC_001");
        }
    }
}