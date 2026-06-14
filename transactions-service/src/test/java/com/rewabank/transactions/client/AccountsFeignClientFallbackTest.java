package com.rewabank.transactions.client;

import com.rewabank.transactions.dto.AccountDebitCreditRequest;
import com.rewabank.transactions.exception.TransactionException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class AccountsFeignClientFallbackTest {

    private final AccountsFeignClientFallback fallback = new AccountsFeignClientFallback();
    private final AccountDebitCreditRequest   request  = new AccountDebitCreditRequest(null, null, null);

    @Test
    void debitAccount_throwsTransactionExceptionWithTxnAcct001() {
        assertThatThrownBy(() -> fallback.debitAccount(UUID.randomUUID(), request))
                .isInstanceOf(TransactionException.class)
                .hasMessageContaining("Account service unavailable");
    }

    @Test
    void debitAccount_exceptionHasCorrectErrorCode() {
        try {
            fallback.debitAccount(UUID.randomUUID(), request);
        } catch (TransactionException ex) {
            assertThat(ex.getErrorCode()).isEqualTo("TXN_ACCT_001");
        }
    }

    @Test
    void creditAccount_throwsTransactionExceptionWithTxnAcct002() {
        assertThatThrownBy(() -> fallback.creditAccount(UUID.randomUUID(), request))
                .isInstanceOf(TransactionException.class)
                .hasMessageContaining("Account service unavailable");
    }

    @Test
    void creditAccount_exceptionHasCorrectErrorCode() {
        try {
            fallback.creditAccount(UUID.randomUUID(), request);
        } catch (TransactionException ex) {
            assertThat(ex.getErrorCode()).isEqualTo("TXN_ACCT_002");
        }
    }
}