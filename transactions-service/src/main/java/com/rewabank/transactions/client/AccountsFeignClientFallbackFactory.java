package com.rewabank.transactions.client;

import com.rewabank.transactions.dto.AccountDebitCreditRequest;
import com.rewabank.transactions.dto.AccountInfoResponse;
import com.rewabank.transactions.exception.TransactionException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class AccountsFeignClientFallbackFactory implements FallbackFactory<AccountsFeignClient> {

    @Override
    public AccountsFeignClient create(Throwable cause) {
        return new AccountsFeignClient() {

            @Override
            public AccountInfoResponse getById(UUID accountId) {
                return handleLookupFailure(cause, "getById", accountId.toString(),
                        "TXN_ACCT_003", "Cannot verify source account");
            }

            @Override
            public AccountInfoResponse lookupByAccountNumber(String accountNumber) {
                return handleLookupFailure(cause, "lookupByAccountNumber", accountNumber,
                        "TXN_ACCT_004", "Cannot resolve destination account");
            }

            @Override
            public void debitAccount(UUID accountId, AccountDebitCreditRequest request) {
                log.error("Accounts MS debit failed — accountId: {} cause: {}",
                        accountId, cause.getMessage());
                if (cause instanceof FeignException fe && fe.status() == 409) {
                    throw new TransactionException("TXN_ACCT_007",
                            "Insufficient balance or account locked");
                }
                throw new TransactionException("TXN_ACCT_001",
                        "Account service unavailable. Transaction cannot proceed.");
            }

            @Override
            public void creditAccount(UUID accountId, AccountDebitCreditRequest request) {
                log.error("Accounts MS credit failed — accountId: {} cause: {}",
                        accountId, cause.getMessage());
                throw new TransactionException("TXN_ACCT_002",
                        "Account service unavailable. Credit could not be completed.");
            }
        };
    }

    private AccountInfoResponse handleLookupFailure(Throwable cause, String op,
                                                     String key, String unavailableCode,
                                                     String unavailableMsg) {
        if (cause instanceof FeignException fe) {
            log.warn("Accounts MS {} returned {} for key: {}", op, fe.status(), key);
            if (fe.status() == 404) {
                throw new TransactionException("TXN_ACCT_005", "Account not found: " + key);
            }
            if (fe.status() == 403) {
                log.error("Istio/authz denied {} to accounts-service — check AuthorizationPolicy for transactions-ms-sa", op);
                throw new TransactionException("TXN_ACCT_006",
                        "Account service access denied. Contact support.");
            }
        }
        log.error("Accounts MS unavailable for {} — key: {} cause: {}", op, key, cause.getMessage());
        throw new TransactionException(unavailableCode, unavailableMsg + ". Please try again.");
    }
}
