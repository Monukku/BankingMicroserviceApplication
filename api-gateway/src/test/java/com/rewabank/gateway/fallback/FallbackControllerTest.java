package com.rewabank.gateway.fallback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackControllerTest {

    private FallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new FallbackController();
    }

    @Test
    void contactSupport_returns503WithCorrectErrorCode() {
        assertFallback(controller.contactSupport(), "SERVICE_UNAVAILABLE");
    }

    @Test
    void accountsFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.accountsFallback(), "ACCT_SERVICE_DOWN");
    }

    @Test
    void transactionsFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.transactionsFallback(), "TXN_SERVICE_DOWN");
    }

    @Test
    void paymentsFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.paymentsFallback(), "PAY_SERVICE_DOWN");
    }

    @Test
    void fraudFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.fraudFallback(), "FRAUD_SERVICE_DOWN");
    }

    @Test
    void loansFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.loansFallback(), "LOAN_SERVICE_DOWN");
    }

    @Test
    void cardsFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.cardsFallback(), "CARD_SERVICE_DOWN");
    }

    @Test
    void customersFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.customersFallback(), "CUST_SERVICE_DOWN");
    }

    @Test
    void transactionsFallback_messageWarnsDontRetry() {
        StepVerifier.create(controller.transactionsFallback())
                .assertNext(resp -> assertThat(resp.getBody())
                        .containsKey("message")
                        .extracting(b -> b.get("message").toString())
                        .asString()
                        .containsIgnoringCase("NOT retry"))
                .verifyComplete();
    }

    @Test
    void authFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.authFallback(), "AUTH_SERVICE_DOWN");
    }

    @Test
    void notificationsFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.notificationsFallback(), "NOTIF_SERVICE_DOWN");
    }

    @Test
    void repaymentFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.repaymentFallback(), "REPAY_SERVICE_DOWN");
    }

    @Test
    void statementsFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.statementsFallback(), "STMT_SERVICE_DOWN");
    }

    @Test
    void reportsFallback_returns503WithCorrectErrorCode() {
        assertFallback(controller.reportsFallback(), "RPT_SERVICE_DOWN");
    }

    @Test
    void allFallbacks_includeTimestampAndStatusFields() {
        StepVerifier.create(controller.accountsFallback())
                .assertNext(resp -> {
                    Map<String, Object> body = resp.getBody();
                    assertThat(body).containsKeys("timestamp", "status", "errorCode", "message");
                    assertThat(body.get("status")).isEqualTo(503);
                })
                .verifyComplete();
    }

    private void assertFallback(reactor.core.publisher.Mono<ResponseEntity<Map<String, Object>>> mono,
                                String expectedErrorCode) {
        StepVerifier.create(mono)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(response.getBody()).containsEntry("errorCode", expectedErrorCode);
                })
                .verifyComplete();
    }
}