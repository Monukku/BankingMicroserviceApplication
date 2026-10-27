package com.rewabank.accounts.services;

import com.rewabank.accounts.client.CustomersFeignClient;
import com.rewabank.accounts.dto.*;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.exception.AccountException;
import com.rewabank.accounts.repository.AccountsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositService {

    private final AccountsRepository   accountRepository;
    private final CustomersFeignClient customersFeignClient;
    private final AccountReadService   accountReadService;
    private final OutboxEventSaver     outboxEventSaver;
    private final AccountService       accountService;  // reuse toResponse + generateAccountNumber

    private static final String TOPIC_ACCOUNT_CREATED = "bank.account.created";
    private static final String EVT_ACCOUNT_CREATED   = "ACCOUNT_CREATED";

    // RBI-published interest rate table (annualised, compounded quarterly)
    private static final java.util.Map<Integer, BigDecimal> FD_RATES = java.util.Map.of(
            6,   new BigDecimal("6.50"),
            12,  new BigDecimal("7.00"),
            24,  new BigDecimal("7.50"),
            36,  new BigDecimal("8.00"),
            60,  new BigDecimal("8.50"),
            120, new BigDecimal("8.25")
    );

    // ── Open Fixed Deposit ────────────────────────────────────────────────────
    @Transactional
    public AccountResponse openFixedDeposit(String keycloakUserId,
                                             UUID customerId,
                                             FixedDepositRequest req) {
        // KYC gate
        assertKycVerified(customerId);

        // Max 5 concurrent FDs per customer
        long activeFds = accountRepository.countActiveFdsByCustomer(customerId);
        if (activeFds >= 5) {
            throw new AccountException("ACCT_FD_001",
                    "Maximum 5 active Fixed Deposits allowed per customer");
        }

        // Source account must be ACTIVE SAVINGS or CURRENT owned by this user
        Account source = accountRepository
                .findByIdAndDeletedAtIsNull(req.sourceAccountId())
                .orElseThrow(() -> new AccountException("ACCT_002",
                        "Source account not found"));

        assertOwnership(source, keycloakUserId);
        assertFundableAccountType(source);
        assertActiveStatus(source);

        // Balance check: must have depositAmount + source minimumBalance remaining
        BigDecimal available = source.getBalance().subtract(source.getMinimumBalance());
        if (available.compareTo(req.depositAmount()) < 0) {
            throw new AccountException("ACCT_FD_002",
                    "Insufficient balance. Available after minimum balance: ₹" + available);
        }

        // Determine interest rate — find closest standard tenure or reject
        BigDecimal interestRate = resolveInterestRate(req.tenureMonths());

        // Debit source account (intra-service — no Saga needed for product placement)
        source.setBalance(source.getBalance().subtract(req.depositAmount()));
        source.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(source);
        accountReadService.updateBalanceCache(source);

        // Create FD account (ACTIVE immediately — money already moved)
        LocalDate maturityDate = LocalDate.now().plusMonths(req.tenureMonths());
        String accountNumber = accountService.generateAccountNumber();

        Account fd = Account.builder()
                .accountNumber(accountNumber)
                .keycloakUserId(keycloakUserId)
                .customerId(customerId)
                .accountType(Account.AccountType.FIXED_DEPOSIT)
                .status(Account.AccountStatus.ACTIVE)
                .balance(req.depositAmount())
                .minimumBalance(BigDecimal.ZERO)
                .interestRate(interestRate)
                .currency("INR")
                .branchCode(req.branchCode())
                .ifscCode(req.ifscCode())
                .depositAmount(req.depositAmount())
                .tenureMonths(req.tenureMonths())
                .maturityDate(maturityDate)
                .sourceAccountId(req.sourceAccountId())
                .activatedAt(LocalDateTime.now())
                .build();

        Account saved = accountRepository.save(fd);
        accountReadService.updateBalanceCache(saved);

        outboxEventSaver.save(saved.getId().toString(),
                EVT_ACCOUNT_CREATED, TOPIC_ACCOUNT_CREATED,
                buildPayload(saved, EVT_ACCOUNT_CREATED));

        log.info("FD opened: {} amount: ₹{} tenure: {}m maturity: {} for customer: {}",
                accountNumber, req.depositAmount(), req.tenureMonths(), maturityDate, customerId);

        return accountService.toResponse(saved);
    }

    // ── Open Recurring Deposit ────────────────────────────────────────────────
    @Transactional
    public AccountResponse openRecurringDeposit(String keycloakUserId,
                                                 UUID customerId,
                                                 RecurringDepositRequest req) {
        assertKycVerified(customerId);

        // Max 3 concurrent RDs per customer
        long activeRds = accountRepository.countActiveRdsByCustomer(customerId);
        if (activeRds >= 3) {
            throw new AccountException("ACCT_RD_001",
                    "Maximum 3 active Recurring Deposits allowed per customer");
        }

        Account source = accountRepository
                .findByIdAndDeletedAtIsNull(req.sourceAccountId())
                .orElseThrow(() -> new AccountException("ACCT_002",
                        "Source account not found"));

        assertOwnership(source, keycloakUserId);
        assertFundableAccountType(source);
        assertActiveStatus(source);

        // First installment debit — must have it available now
        BigDecimal available = source.getBalance().subtract(source.getMinimumBalance());
        if (available.compareTo(req.installmentAmount()) < 0) {
            throw new AccountException("ACCT_RD_002",
                    "Insufficient balance for first installment. Available: ₹" + available);
        }

        BigDecimal interestRate = resolveInterestRate(req.tenureMonths());
        LocalDate today = LocalDate.now();

        // Debit first installment from source
        source.setBalance(source.getBalance().subtract(req.installmentAmount()));
        source.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(source);
        accountReadService.updateBalanceCache(source);

        String accountNumber = accountService.generateAccountNumber();

        Account rd = Account.builder()
                .accountNumber(accountNumber)
                .keycloakUserId(keycloakUserId)
                .customerId(customerId)
                .accountType(Account.AccountType.RECURRING_DEPOSIT)
                .status(Account.AccountStatus.ACTIVE)
                .balance(req.installmentAmount())     // first installment credited
                .minimumBalance(BigDecimal.ZERO)
                .interestRate(interestRate)
                .currency("INR")
                .branchCode(req.branchCode())
                .ifscCode(req.ifscCode())
                .installmentAmount(req.installmentAmount())
                .tenureMonths(req.tenureMonths())
                .maturityDate(today.plusMonths(req.tenureMonths()))
                .nextInstallmentDate(today.plusMonths(1))
                .sourceAccountId(req.sourceAccountId())
                .activatedAt(LocalDateTime.now())
                .build();

        Account saved = accountRepository.save(rd);
        accountReadService.updateBalanceCache(saved);

        outboxEventSaver.save(saved.getId().toString(),
                EVT_ACCOUNT_CREATED, TOPIC_ACCOUNT_CREATED,
                buildPayload(saved, EVT_ACCOUNT_CREATED));

        log.info("RD opened: {} installment: ₹{} tenure: {}m for customer: {}",
                accountNumber, req.installmentAmount(), req.tenureMonths(), customerId);

        return accountService.toResponse(saved);
    }

    // ── Open Salary Account (staff only) ─────────────────────────────────────
    @Transactional
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    public AccountResponse openSalaryAccount(String staffKeycloakId,
                                              SalaryAccountRequest req) {
        // Verify target customer exists and is KYC verified
        assertKycVerified(req.customerId());

        // Only one active SALARY account per customer
        boolean exists = accountRepository
                .existsByCustomerIdAndAccountTypeAndStatusAndDeletedAtIsNull(
                        req.customerId(),
                        Account.AccountType.SALARY,
                        Account.AccountStatus.ACTIVE);
        if (exists) {
            throw new AccountException("ACCT_SAL_001",
                    "Customer already has an active Salary account");
        }

        // Also check PENDING salary accounts to prevent double-open
        boolean pendingExists = accountRepository
                .existsByCustomerIdAndAccountTypeAndStatusAndDeletedAtIsNull(
                        req.customerId(),
                        Account.AccountType.SALARY,
                        Account.AccountStatus.PENDING);
        if (pendingExists) {
            throw new AccountException("ACCT_SAL_002",
                    "A Salary account is already pending activation for this customer");
        }

        // Fetch customer's keycloakUserId via KYC response
        KycStatusResponse kyc = customersFeignClient.getKycStatus(req.customerId());
        String customerKeycloakId = kyc.keycloakUserId();

        String accountNumber = accountService.generateAccountNumber();

        Account salary = Account.builder()
                .accountNumber(accountNumber)
                .keycloakUserId(customerKeycloakId)
                .customerId(req.customerId())
                .accountType(Account.AccountType.SALARY)
                .status(Account.AccountStatus.ACTIVE)   // KYC already verified
                .balance(BigDecimal.ZERO)
                .minimumBalance(BigDecimal.ZERO)        // Salary accounts have no min balance
                .currency("INR")
                .branchCode(req.branchCode())
                .ifscCode(req.ifscCode())
                .employerName(req.employerName())
                .employerCode(req.employerCode())
                .activatedAt(LocalDateTime.now())
                .build();

        Account saved = accountRepository.save(salary);

        outboxEventSaver.save(saved.getId().toString(),
                EVT_ACCOUNT_CREATED, TOPIC_ACCOUNT_CREATED,
                buildPayload(saved, EVT_ACCOUNT_CREATED));

        log.info("Salary account opened: {} employer: {} by staff: {} for customer: {}",
                accountNumber, req.employerName(), staffKeycloakId, req.customerId());

        return accountService.toResponse(saved);
    }

    // ── FD Maturity Payout (called by scheduler) ──────────────────────────────
    @Transactional
    public void processFdMaturity(UUID fdAccountId) {
        Account fd = accountRepository
                .findByIdAndDeletedAtIsNull(fdAccountId)
                .orElseThrow(() -> new AccountException("ACCT_002", "FD account not found"));

        if (fd.getAccountType() != Account.AccountType.FIXED_DEPOSIT) {
            throw new AccountException("ACCT_FD_003", "Account is not a Fixed Deposit");
        }
        if (fd.getStatus() != Account.AccountStatus.ACTIVE) {
            return; // Already matured or closed — skip
        }
        if (fd.getMaturedAt() != null) {
            return; // Already processed
        }

        // Calculate maturity amount: P(1 + r/4)^(4n) — quarterly compounding
        BigDecimal principal    = fd.getDepositAmount();
        BigDecimal annualRate   = fd.getInterestRate().divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        double     years        = fd.getTenureMonths() / 12.0;
        double     maturityAmt  = principal.doubleValue()
                * Math.pow(1 + annualRate.doubleValue() / 4, 4 * years);
        BigDecimal maturityAmount = BigDecimal.valueOf(maturityAmt)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal interest = maturityAmount.subtract(principal);

        // Pay maturity amount to source savings account (if it still exists and is ACTIVE)
        if (fd.getSourceAccountId() != null) {
            accountRepository.findByIdAndDeletedAtIsNull(fd.getSourceAccountId())
                    .filter(src -> src.getStatus() == Account.AccountStatus.ACTIVE)
                    .ifPresent(src -> {
                        src.setBalance(src.getBalance().add(maturityAmount));
                        src.setLastTransactionAt(LocalDateTime.now());
                        accountRepository.save(src);
                        accountReadService.updateBalanceCache(src);
                        log.info("FD maturity payout ₹{} (principal ₹{} + interest ₹{}) → account {}",
                                maturityAmount, principal, interest, src.getAccountNumber());
                    });
        }

        // Close FD account
        fd.setStatus(Account.AccountStatus.CLOSED);
        fd.setBalance(BigDecimal.ZERO);
        fd.setMaturedAt(LocalDateTime.now());
        fd.setClosedAt(LocalDateTime.now());
        accountRepository.save(fd);
        accountReadService.evictBalanceCache(fd.getAccountNumber());

        outboxEventSaver.save(fd.getId().toString(),
                "FD_MATURED", "bank.fd.matured",
                buildPayload(fd, "FD_MATURED"));

        log.info("FD matured: {} maturityAmount: ₹{}", fd.getAccountNumber(), maturityAmount);
    }

    // ── RD Monthly Installment Debit (called by scheduler) ───────────────────
    @Transactional
    public void processRdInstallment(UUID rdAccountId) {
        Account rd = accountRepository
                .findByIdAndDeletedAtIsNull(rdAccountId)
                .orElseThrow(() -> new AccountException("ACCT_002", "RD account not found"));

        if (rd.getAccountType() != Account.AccountType.RECURRING_DEPOSIT) {
            throw new AccountException("ACCT_RD_003", "Account is not a Recurring Deposit");
        }
        if (rd.getStatus() != Account.AccountStatus.ACTIVE) return;

        // Debit from source savings
        if (rd.getSourceAccountId() == null) {
            log.warn("RD {} has no source account — skipping installment", rd.getAccountNumber());
            return;
        }

        Account source = accountRepository
                .findByIdAndDeletedAtIsNull(rd.getSourceAccountId())
                .orElse(null);

        if (source == null || source.getStatus() != Account.AccountStatus.ACTIVE) {
            // Source account gone or frozen — freeze the RD
            rd.setStatus(Account.AccountStatus.FROZEN);
            rd.setFrozenAt(LocalDateTime.now());
            rd.setFrozenReason("Source account unavailable for installment debit");
            accountRepository.save(rd);
            log.warn("RD {} frozen — source account {} unavailable",
                    rd.getAccountNumber(), rd.getSourceAccountId());
            return;
        }

        BigDecimal available = source.getBalance().subtract(source.getMinimumBalance());
        if (available.compareTo(rd.getInstallmentAmount()) < 0) {
            // Insufficient funds — freeze RD after failed debit
            rd.setStatus(Account.AccountStatus.FROZEN);
            rd.setFrozenAt(LocalDateTime.now());
            rd.setFrozenReason("Insufficient funds for installment on "
                    + rd.getNextInstallmentDate());
            accountRepository.save(rd);
            log.warn("RD {} frozen — insufficient funds in source account {}",
                    rd.getAccountNumber(), source.getAccountNumber());
            return;
        }

        // Debit source, credit RD
        source.setBalance(source.getBalance().subtract(rd.getInstallmentAmount()));
        source.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(source);
        accountReadService.updateBalanceCache(source);

        rd.setBalance(rd.getBalance().add(rd.getInstallmentAmount()));
        rd.setNextInstallmentDate(rd.getNextInstallmentDate().plusMonths(1));
        rd.setLastTransactionAt(LocalDateTime.now());

        // Check if this was the final installment
        if (!LocalDate.now().isBefore(rd.getMaturityDate())) {
            processRdMaturityPayout(rd);
        } else {
            accountRepository.save(rd);
            accountReadService.updateBalanceCache(rd);
        }

        log.info("RD installment ₹{} debited from {} → RD {}. Next: {}",
                rd.getInstallmentAmount(), source.getAccountNumber(),
                rd.getAccountNumber(), rd.getNextInstallmentDate());
    }

    // ── Get customer's FDs ────────────────────────────────────────────────────
    public List<AccountResponse> getMyFixedDeposits(String keycloakUserId) {
        return accountRepository
                .findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
                .stream()
                .filter(a -> a.getAccountType() == Account.AccountType.FIXED_DEPOSIT)
                .map(accountService::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get customer's RDs ────────────────────────────────────────────────────
    public List<AccountResponse> getMyRecurringDeposits(String keycloakUserId) {
        return accountRepository
                .findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
                .stream()
                .filter(a -> a.getAccountType() == Account.AccountType.RECURRING_DEPOSIT)
                .map(accountService::toResponse)
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void processRdMaturityPayout(Account rd) {
        BigDecimal annualRate  = rd.getInterestRate()
                .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        // A = R * [(1+r/12)^n - 1] / (r/12) * (1+r/12)
        double monthlyRate  = annualRate.doubleValue() / 12;
        double maturityAmt  = rd.getInstallmentAmount().doubleValue()
                * (Math.pow(1 + monthlyRate, rd.getTenureMonths()) - 1)
                / monthlyRate * (1 + monthlyRate);
        BigDecimal maturityAmount = BigDecimal.valueOf(maturityAmt)
                .setScale(4, RoundingMode.HALF_UP);

        // Pay to source account
        if (rd.getSourceAccountId() != null) {
            accountRepository.findByIdAndDeletedAtIsNull(rd.getSourceAccountId())
                    .filter(src -> src.getStatus() == Account.AccountStatus.ACTIVE)
                    .ifPresent(src -> {
                        src.setBalance(src.getBalance().add(maturityAmount));
                        src.setLastTransactionAt(LocalDateTime.now());
                        accountRepository.save(src);
                        accountReadService.updateBalanceCache(src);
                        log.info("RD maturity payout ₹{} → savings {}", maturityAmount, src.getAccountNumber());
                    });
        }

        rd.setStatus(Account.AccountStatus.CLOSED);
        rd.setBalance(BigDecimal.ZERO);
        rd.setMaturedAt(LocalDateTime.now());
        rd.setClosedAt(LocalDateTime.now());
        accountRepository.save(rd);
        accountReadService.evictBalanceCache(rd.getAccountNumber());

        outboxEventSaver.save(rd.getId().toString(),
                "RD_MATURED", "bank.rd.matured",
                buildPayload(rd, "RD_MATURED"));
    }

    private BigDecimal resolveInterestRate(int tenureMonths) {
        // Exact match first
        if (FD_RATES.containsKey(tenureMonths)) return FD_RATES.get(tenureMonths);
        // Find the next standard tenure bracket
        return FD_RATES.entrySet().stream()
                .filter(e -> e.getKey() <= tenureMonths)
                .max(java.util.Comparator.comparingInt(java.util.Map.Entry::getKey))
                .map(java.util.Map.Entry::getValue)
                .orElse(new BigDecimal("6.50"));
    }

    private void assertKycVerified(UUID customerId) {
        try {
            KycStatusResponse kyc = customersFeignClient.getKycStatus(customerId);
            if (!kyc.kycVerified()) {
                throw new AccountException("ACCT_004",
                        "KYC not verified. Cannot open deposit account. Status: " + kyc.kycStatus());
            }
        } catch (AccountException e) {
            throw e;
        } catch (Exception e) {
            throw new AccountException("ACCT_KYC_001",
                    "KYC service unavailable. Please try again later.");
        }
    }

    private void assertOwnership(Account account, String keycloakUserId) {
        if (!account.getKeycloakUserId().equals(keycloakUserId)) {
            throw new AccountException("ACCT_009",
                    "Account does not belong to the requesting user");
        }
    }

    private void assertFundableAccountType(Account account) {
        if (account.getAccountType() != Account.AccountType.SAVINGS
                && account.getAccountType() != Account.AccountType.CURRENT
                && account.getAccountType() != Account.AccountType.BUSINESS) {
            throw new AccountException("ACCT_FD_004",
                    "Source account must be a SAVINGS, CURRENT, or BUSINESS account");
        }
    }

    private void assertActiveStatus(Account account) {
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new AccountException("ACCT_FD_005",
                    "Source account must be ACTIVE. Current status: " + account.getStatus());
        }
    }

    private java.util.Map<String, Object> buildPayload(Account a, String eventType) {
        java.util.Map<String, Object> p = new java.util.HashMap<>();
        p.put("eventType",    eventType);
        p.put("accountId",    a.getId().toString());
        p.put("accountNumber", a.getAccountNumber());
        p.put("customerId",   a.getCustomerId().toString());
        p.put("accountType",  a.getAccountType().name());
        p.put("status",       a.getStatus().name());
        p.put("occurredAt",   LocalDateTime.now().toString());
        if (a.getDepositAmount()    != null) p.put("depositAmount",    a.getDepositAmount().toString());
        if (a.getTenureMonths()     != null) p.put("tenureMonths",     a.getTenureMonths());
        if (a.getMaturityDate()     != null) p.put("maturityDate",     a.getMaturityDate().toString());
        if (a.getInstallmentAmount() != null) p.put("installmentAmount", a.getInstallmentAmount().toString());
        return p;
    }
}
