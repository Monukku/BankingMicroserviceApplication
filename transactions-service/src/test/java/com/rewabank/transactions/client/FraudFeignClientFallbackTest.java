package com.rewabank.transactions.client;

import com.rewabank.transactions.dto.FraudScoreResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FraudFeignClientFallbackTest {

    private final FraudFeignClientFallback fallback = new FraudFeignClientFallback();

    @Test
    void getFraudScore_returnsNonNull() {
        // Kills NullReturn mutation on the return value
        FraudScoreResponse response = fallback.getFraudScore(
                UUID.randomUUID(), new BigDecimal("500.00"), "TRANSFER", "corr-1");
        assertThat(response).isNotNull();
    }

    @Test
    void getFraudScore_alwaysReturnsFlagNotApprove() {
        // Kills EmptyObjectReturn + verifies fail-safe contract: never silently APPROVE
        FraudScoreResponse response = fallback.getFraudScore(
                UUID.randomUUID(), new BigDecimal("1000.00"), "TRANSFER", "corr-2");
        assertThat(response.action()).isEqualTo("FLAG");
        assertThat(response.action()).isNotEqualTo("APPROVE");
    }

    @Test
    void getFraudScore_returnsHighRiskyScore() {
        // Kills mutations that change the score value
        FraudScoreResponse response = fallback.getFraudScore(
                UUID.randomUUID(), BigDecimal.TEN, "TRANSFER", null);
        assertThat(response.score()).isEqualTo(75);
    }

    @Test
    void getFraudScore_responseContainsAccountId() {
        // Kills NullReturn on accountId.toString()
        UUID accountId = UUID.randomUUID();
        FraudScoreResponse response = fallback.getFraudScore(
                accountId, BigDecimal.ONE, "TRANSFER", "corr-3");
        assertThat(response.accountId()).isEqualTo(accountId.toString());
    }
}