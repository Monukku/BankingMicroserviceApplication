package com.rewabank.transactions.client;

import com.rewabank.transactions.dto.AccountDebitCreditRequest;
import com.rewabank.transactions.dto.AccountInfoResponse;
import com.rewabank.transactions.exception.TransactionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class AccountsFeignClientFallback implements AccountsFeignClient {

    @Override
    public AccountInfoResponse getById(UUID accountId) {
        log.error("Accounts MS unavailable for getById — accountId: {}", accountId);
        throw new TransactionException("TXN_ACCT_003",
                "Account service unavailable. Cannot verify account.");
    }

    @Override
    public AccountInfoResponse lookupByAccountNumber(String accountNumber) {
        log.error("Accounts MS unavailable for lookup — accountNumber: {}", accountNumber);
        throw new TransactionException("TXN_ACCT_004",
                "Account service unavailable. Cannot resolve destination account.");
    }

    @Override
    public void debitAccount(UUID accountId, AccountDebitCreditRequest request) {
        log.error("Accounts MS unavailable for debit — accountId: {}", accountId);
        throw new TransactionException("TXN_ACCT_001",
                "Account service unavailable. Transaction cannot proceed.");
    }

    @Override
    public void creditAccount(UUID accountId, AccountDebitCreditRequest request) {
        log.error("Accounts MS unavailable for credit — accountId: {}", accountId);
        throw new TransactionException("TXN_ACCT_002",
                "Account service unavailable. Credit could not be completed.");
    }
}
