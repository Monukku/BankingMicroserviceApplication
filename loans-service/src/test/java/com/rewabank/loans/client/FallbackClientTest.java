package com.rewabank.loans.client;

import com.rewabank.loans.dto.AccountCreditRequest;
import com.rewabank.loans.exception.LoanException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class FallbackClientTest {

    private final AccountsFeignClientFallback accountsFallback = new AccountsFeignClientFallback();
    private final CustomersFeignClientFallback customersFallback = new CustomersFeignClientFallback();
    private final FraudFeignClientFallback fraudFallback = new FraudFeignClientFallback();

    private final AccountCreditRequest request =
            new AccountCreditRequest(UUID.randomUUID(), BigDecimal.TEN, "REF-001");

    // ── AccountsFeignClientFallback ────────────────────────────────────────────

    @Test
    void accountsFallback_creditAccount_throwsLoanException() {
        assertThatThrownBy(() -> accountsFallback.creditAccount(UUID.randomUUID(), request))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("Account service unavailable");
    }

    // ── CustomersFeignClientFallback ───────────────────────────────────────────

    @Test
    void customersFallback_creditAccount_throwsLoanException() {
        assertThatThrownBy(() -> customersFallback.creditAccount(UUID.randomUUID(), request))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("Account service unavailable");
    }

    // ── FraudFeignClientFallback ───────────────────────────────────────────────

    @Test
    void fraudFallback_getScore_returnsNonNull() {
        // Kills NullReturn on the return value
        Map<String, Object> result = fraudFallback.getScore(
                UUID.randomUUID(), new BigDecimal("50000"), "LOAN", "corr-1");
        assertThat(result).isNotNull();
    }

    @Test
    void fraudFallback_getScore_returnsAllowAction() {
        // Kills EmptyObjectReturn on Map.of(...) — verifies the safe default action
        Map<String, Object> result = fraudFallback.getScore(
                UUID.randomUUID(), new BigDecimal("50000"), "LOAN", "corr-2");
        assertThat(result.get("action")).isEqualTo("ALLOW");
        assertThat(result.get("score")).isEqualTo(0);
    }

    @Test
    void fraudFallback_getScore_includesAccountId() {
        // Kills NullReturn on accountId.toString()
        UUID accountId = UUID.randomUUID();
        Map<String, Object> result = fraudFallback.getScore(
                accountId, BigDecimal.ONE, "LOAN", "corr-3");
        assertThat(result.get("accountId")).isEqualTo(accountId.toString());
    }
}