package com.rewabank.loans.client;

import com.rewabank.loans.dto.AccountCreditRequest;
import com.rewabank.loans.exception.LoanException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@Slf4j
public class AccountsFeignClientFallback implements AccountsFeignClient {

    @Override
    public void creditAccount(UUID accountId, AccountCreditRequest request) {
        log.error("Accounts MS unavailable for credit — accountId: {}", accountId);
        throw new LoanException("LOAN_ACCT_001",
                "Account service unavailable. Disbursement cannot proceed.");
    }
}