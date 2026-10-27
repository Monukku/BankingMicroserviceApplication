package com.rewabank.accounts.services;

import com.rewabank.accounts.client.CustomersFeignClient;
import com.rewabank.accounts.dto.AccountCreateRequest;
import com.rewabank.accounts.dto.AccountLookupResponse;
import com.rewabank.accounts.dto.AccountResponse;
import com.rewabank.accounts.dto.KycStatusResponse;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.exception.AccountException;
import com.rewabank.accounts.repository.AccountsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountsRepository           accountRepository;
    private final CustomersFeignClient         customersFeignClient;
    private final AccountReadService           accountReadService;
    private final OutboxEventSaver             outboxEventSaver;
    private final AccountAccessPolicy          accessPolicy;

    private final SecureRandom secureRandom = new SecureRandom();

    private static final String ERR_ACCOUNT_NOT_FOUND   = "Account not found";
    private static final String ERR_CODE_NOT_FOUND      = "ACCT_002";

    private static final String TOPIC_ACCOUNT_CREATED   = "bank.account.created";
    private static final String TOPIC_ACCOUNT_ACTIVATED = "bank.account.activated";
    private static final String TOPIC_ACCOUNT_FROZEN    = "bank.account.frozen";
    private static final String TOPIC_ACCOUNT_CLOSED    = "bank.account.closed";
    private static final String TOPIC_BALANCE_UPDATED   = "bank.balance.updated";

    private static final String EVT_BALANCE_UPDATED   = "BALANCE_UPDATED";
    private static final String EVT_ACCOUNT_CREATED   = "ACCOUNT_CREATED";
    private static final String EVT_ACCOUNT_ACTIVATED = "ACCOUNT_ACTIVATED";
    private static final String EVT_ACCOUNT_FROZEN    = "ACCOUNT_FROZEN";
    private static final String EVT_ACCOUNT_CLOSED    = "ACCOUNT_CLOSED";

    // ── Create account ────────────────────────────────────────────────────────
    @Transactional
    public AccountResponse createAccount(String keycloakUserId,
                                         UUID customerId,
                                         AccountCreateRequest request) {
        // Check for duplicate active account of same type
        List<Account> existing = accountRepository
                .findByCustomerIdAndDeletedAtIsNull(customerId);
        boolean duplicate = existing.stream()
                .anyMatch(a -> a.getAccountType() == request.accountType() &&
                        a.getStatus() != Account.AccountStatus.CLOSED);
        if (duplicate) {
            throw new AccountException("ACCT_001",
                    "Active account of type " + request.accountType() + " already exists");
        }

        String accountNumber = generateAccountNumber();

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .keycloakUserId(keycloakUserId)
                .customerId(customerId)
                .accountType(request.accountType())
                .status(Account.AccountStatus.PENDING)
                .balance(BigDecimal.ZERO)
                .minimumBalance(minimumBalanceFor(request.accountType()))
                .branchCode(request.branchCode())
                .ifscCode(request.ifscCode())
                .build();

        Account savedAccount = accountRepository.save(account);

        // Fetch KYC response to enrich Kafka event with contact details (best-effort)
        KycStatusResponse kyc = null;
        try {
            kyc = customersFeignClient.getKycStatus(customerId);
        } catch (Exception e) {
            log.warn("Could not fetch KYC contact details for event enrichment — sending without: {}", e.getMessage());
        }

        // Save outbox event in SAME transaction — guaranteed delivery
        outboxEventSaver.save(savedAccount.getId().toString(),
                EVT_ACCOUNT_CREATED, TOPIC_ACCOUNT_CREATED,
                buildAccountPayload(savedAccount, EVT_ACCOUNT_CREATED, kyc));

        log.info("Account created: {} status: PENDING for customer: {}",
                accountNumber, customerId);

        return toResponse(savedAccount);
    }

    // ── Activate account (called after KYC verified) ──────────────────────────
    @Transactional
    public AccountResponse activateAccount(UUID accountId) {
        Account account = accountRepository
                .findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new AccountException(ERR_CODE_NOT_FOUND, ERR_ACCOUNT_NOT_FOUND));

        if (!account.canActivate()) {
            throw new AccountException("ACCT_003",
                    "Account cannot be activated from state: " + account.getStatus());
        }

        // Guard: prevent activating a duplicate account type for this customer
        if (accountRepository.existsByCustomerIdAndAccountTypeAndStatusAndDeletedAtIsNull(
                account.getCustomerId(), account.getAccountType(), Account.AccountStatus.ACTIVE)) {
            throw new AccountException("ACCT_003",
                    "Customer already has an active " + account.getAccountType() + " account");
        }

        // KYC gate — sync call to Customers MS
        KycStatusResponse kyc = customersFeignClient
                .getKycStatus(account.getCustomerId());

        if (!kyc.kycVerified()) {
            throw new AccountException("ACCT_004",
                    "KYC not verified. Account cannot be activated. KYC status: "
                            + kyc.kycStatus());
        }

        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setActivatedAt(LocalDateTime.now());
        accountRepository.save(account);

        // Update CQRS read side in Redis
        accountReadService.updateBalanceCache(account);

        // Outbox event
        outboxEventSaver.save(account.getId().toString(),
                EVT_ACCOUNT_ACTIVATED, TOPIC_ACCOUNT_ACTIVATED,
                buildAccountPayload(account, EVT_ACCOUNT_ACTIVATED));

        log.info("Account activated: {}", account.getAccountNumber());
        return toResponse(account);
    }

    // ── Credit balance (called by Transactions MS) ────────────────────────────
    @Transactional
    public AccountResponse credit(UUID accountId, BigDecimal amount,
                                  String correlationId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountException("ACCT_005", "Credit amount must be positive");
        }

        // Pessimistic lock — prevents race conditions
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountException(ERR_CODE_NOT_FOUND, ERR_ACCOUNT_NOT_FOUND));

        if (!account.canTransact()) {
            throw new AccountException("ACCT_006",
                    "Account is not active for transactions. Status: " + account.getStatus());
        }

        BigDecimal previousBalance = account.getBalance();
        account.setBalance(account.getBalance().add(amount));
        account.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(account);

        // Sync Redis read side
        accountReadService.updateBalanceCache(account);

        // Outbox event
        outboxEventSaver.save(account.getId().toString(),
                EVT_BALANCE_UPDATED, TOPIC_BALANCE_UPDATED,
                buildBalancePayload(account, amount, "CREDIT",
                        previousBalance, correlationId));

        log.info("Credit ₹{} to account {} new balance: {}",
                amount, account.getAccountNumber(), account.getBalance());

        return toResponse(account);
    }

    // ── Debit balance (called by Transactions MS) ─────────────────────────────
    @Transactional
    public AccountResponse debit(UUID accountId, BigDecimal amount,
                                 String correlationId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountException("ACCT_005", "Debit amount must be positive");
        }

        // Pessimistic lock
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountException(ERR_CODE_NOT_FOUND, ERR_ACCOUNT_NOT_FOUND));

        if (!account.canTransact()) {
            throw new AccountException("ACCT_006",
                    "Account is not active for transactions. Status: " + account.getStatus());
        }

        // Insufficient balance check
        BigDecimal availableBalance = account.getBalance()
                .subtract(account.getMinimumBalance());
        if (availableBalance.compareTo(amount) < 0) {
            throw new AccountException("ACCT_007",
                    "Insufficient balance. Available: ₹" + availableBalance);
        }

        BigDecimal previousBalance = account.getBalance();
        account.setBalance(account.getBalance().subtract(amount));
        account.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(account);

        // Sync Redis read side
        accountReadService.updateBalanceCache(account);

        // Outbox event
        outboxEventSaver.save(account.getId().toString(),
                EVT_BALANCE_UPDATED, TOPIC_BALANCE_UPDATED,
                buildBalancePayload(account, amount, "DEBIT",
                        previousBalance, correlationId));

        log.info("Debit ₹{} from account {} new balance: {}",
                amount, account.getAccountNumber(), account.getBalance());

        return toResponse(account);
    }

    // ── Reject pending account (teller) ──────────────────────────────────────
    @Transactional
    public AccountResponse rejectAccount(UUID accountId, String reason) {
        Account account = accountRepository
                .findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new AccountException(ERR_CODE_NOT_FOUND, ERR_ACCOUNT_NOT_FOUND));

        if (account.getStatus() != Account.AccountStatus.PENDING) {
            throw new AccountException("ACCT_003",
                    "Only PENDING accounts can be rejected. Current status: " + account.getStatus());
        }

        account.setStatus(Account.AccountStatus.CLOSED);
        account.setClosedAt(LocalDateTime.now());
        account.setFrozenReason(reason != null ? reason : "Rejected by teller");
        accountRepository.save(account);

        outboxEventSaver.save(account.getId().toString(),
                EVT_ACCOUNT_CLOSED, TOPIC_ACCOUNT_CLOSED,
                buildAccountPayload(account, EVT_ACCOUNT_CLOSED));

        log.info("Pending account rejected: {} reason: {}", account.getAccountNumber(),
                reason == null ? "" : reason.replaceAll("[\r\n]", "_"));
        return toResponse(account);
    }

    // ── Freeze account ────────────────────────────────────────────────────────
    @Transactional
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    public AccountResponse freezeAccount(UUID accountId, String reason) {
        Account account = accountRepository
                .findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new AccountException(ERR_CODE_NOT_FOUND, ERR_ACCOUNT_NOT_FOUND));

        if (!account.canFreeze()) {
            throw new AccountException("ACCT_003",
                    "Account cannot be frozen from state: " + account.getStatus());
        }

        account.setStatus(Account.AccountStatus.FROZEN);
        account.setFrozenAt(LocalDateTime.now());
        account.setFrozenReason(reason);
        accountRepository.save(account);

        // Invalidate Redis read side
        accountReadService.evictBalanceCache(account.getAccountNumber());

        outboxEventSaver.save(account.getId().toString(),
                EVT_ACCOUNT_FROZEN, TOPIC_ACCOUNT_FROZEN,
                buildAccountPayload(account, EVT_ACCOUNT_FROZEN));

        log.warn("Account frozen: {} reason: {}",
                account.getAccountNumber(),
                reason == null ? "" : reason.replaceAll("[\r\n]", "_"));
        return toResponse(account);
    }

    // ── Unfreeze account ──────────────────────────────────────────────────────
    @Transactional
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    public AccountResponse unfreezeAccount(UUID accountId) {
        Account account = accountRepository
                .findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new AccountException(ERR_CODE_NOT_FOUND, ERR_ACCOUNT_NOT_FOUND));

        if (!account.canUnfreeze()) {
            throw new AccountException("ACCT_003",
                    "Account cannot be unfrozen from state: " + account.getStatus());
        }

        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setFrozenAt(null);
        account.setFrozenReason(null);
        accountRepository.save(account);

        accountReadService.updateBalanceCache(account);

        outboxEventSaver.save(account.getId().toString(),
                "ACCOUNT_UNFROZEN", "bank.account.unfrozen",
                buildAccountPayload(account, "ACCOUNT_UNFROZEN"));

        log.info("Account unfrozen: {}", account.getAccountNumber());
        return toResponse(account);
    }

    // ── Close account ─────────────────────────────────────────────────────────
    @Transactional
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    public AccountResponse closeAccount(UUID accountId) {
        Account account = accountRepository
                .findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new AccountException(ERR_CODE_NOT_FOUND, ERR_ACCOUNT_NOT_FOUND));

        if (!account.canClose()) {
            throw new AccountException("ACCT_003",
                    "Account cannot be closed from state: " + account.getStatus());
        }

        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new AccountException("ACCT_008",
                    "Cannot close account with positive balance: ₹"
                            + account.getBalance());
        }

        account.setStatus(Account.AccountStatus.CLOSED);
        account.setClosedAt(LocalDateTime.now());
        accountRepository.save(account);

        accountReadService.evictBalanceCache(account.getAccountNumber());

        outboxEventSaver.save(account.getId().toString(),
                EVT_ACCOUNT_CLOSED, TOPIC_ACCOUNT_CLOSED,
                buildAccountPayload(account, EVT_ACCOUNT_CLOSED));

        log.info("Account closed: {}", account.getAccountNumber());
        return toResponse(account);
    }

    // ── Mark dormant (called by DormancyScheduler) ────────────────────────────
    @Transactional
    public void markDormant(UUID accountId) {
        Account account = accountRepository
                .findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new AccountException(ERR_CODE_NOT_FOUND, ERR_ACCOUNT_NOT_FOUND));

        if (account.getStatus() == Account.AccountStatus.ACTIVE) {
            account.setStatus(Account.AccountStatus.DORMANT);
            accountRepository.save(account);

            outboxEventSaver.save(account.getId().toString(),
                    "ACCOUNT_DORMANT", "bank.account.dormant",
                    buildAccountPayload(account, "ACCOUNT_DORMANT"));

            log.info("Account marked dormant: {}", account.getAccountNumber());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    protected String generateAccountNumber() {
        // nextLong(bound) produces uniform distribution; nextDouble() does not
        String number;
        do {
            long raw = 100_000_000_000L + (secureRandom.nextLong(900_000_000_000L));
            number = String.valueOf(raw);
        } while (accountRepository.existsByAccountNumberAndDeletedAtIsNull(number));
        return number;
    }

    @Transactional(readOnly = true)
    public AccountLookupResponse lookupByAccountNumber(String accountNumber) {
        Account account = accountRepository
                .findByAccountNumberAndDeletedAtIsNull(accountNumber)
                .orElseThrow(() -> new AccountException(ERR_CODE_NOT_FOUND, ERR_ACCOUNT_NOT_FOUND));
        return new AccountLookupResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getIfscCode(),
                account.getStatus()
        );
    }

    public static BigDecimal minimumBalanceFor(Account.AccountType type) {
        return switch (type) {
            case SAVINGS           -> new BigDecimal("1000.00");
            case CURRENT           -> new BigDecimal("10000.00");
            case BUSINESS          -> new BigDecimal("5000.00");
            case SALARY,
                 FIXED_DEPOSIT,
                 RECURRING_DEPOSIT -> BigDecimal.ZERO;
        };
    }

    private Map<String, Object> buildAccountPayload(Account a, String eventType) {
        return buildAccountPayload(a, eventType, null);
    }

    private Map<String, Object> buildAccountPayload(Account a, String eventType,
                                                     KycStatusResponse kyc) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("eventType",      eventType);
        payload.put("accountId",      a.getId().toString());
        payload.put("accountNumber",  a.getAccountNumber());
        payload.put("customerId",     a.getCustomerId().toString());
        payload.put("keycloakUserId", a.getKeycloakUserId());
        payload.put("accountType",    a.getAccountType().name());
        payload.put("status",         a.getStatus().name());
        payload.put("currency",       a.getCurrency());
        payload.put("occurredAt",     LocalDateTime.now().toString());
        if (kyc != null) {
            payload.put("fullName",     kyc.fullName()     != null ? kyc.fullName()     : "");
            payload.put("email",        kyc.email()        != null ? kyc.email()        : "");
            payload.put("mobileNumber", kyc.mobileNumber() != null ? kyc.mobileNumber() : "");
        }
        return payload;
    }

    private Map<String, Object> buildBalancePayload(Account a, BigDecimal amount,
                                                    String direction,
                                                    BigDecimal previousBalance,
                                                    String correlationId) {
        return Map.of(
                "eventType",       EVT_BALANCE_UPDATED,
                "accountId",       a.getId().toString(),
                "accountNumber",   a.getAccountNumber(),
                "customerId",      a.getCustomerId().toString(),
                "direction",       direction,
                "amount",          amount.toString(),
                "previousBalance", previousBalance.toString(),
                "newBalance",      a.getBalance().toString(),
                "correlationId",   correlationId != null ? correlationId : "",
                "occurredAt",      LocalDateTime.now().toString()
        );
    }

    public AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId(), a.getAccountNumber(), a.getKeycloakUserId(),
                a.getCustomerId(), a.getAccountType(), a.getStatus(),
                a.getBalance(), a.getCurrency(), a.getBranchCode(),
                a.getIfscCode(), a.getActivatedAt(), a.getCreatedAt(),
                a.getDepositAmount(), a.getTenureMonths(), a.getInterestRate(),
                a.getMaturityDate(), a.getSourceAccountId(),
                a.getInstallmentAmount(), a.getNextInstallmentDate(), a.getMaturedAt(),
                a.getEmployerName(), a.getEmployerCode()
        );
    }

    public List<AccountResponse> getAccountsByUser(String keycloakUserId) {
        return accountRepository
                .findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AccountResponse getById(UUID id) {
        return accountRepository.findByIdAndDeletedAtIsNull(id)
                .map(this::toResponse)
                .orElseThrow(() -> new AccountException(ERR_CODE_NOT_FOUND, ERR_ACCOUNT_NOT_FOUND));
    }

    /**
     * Ownership-guarded get-by-id for the public GET /{uuid} endpoint.
     * Owner or a privileged role (staff / INTERNAL_SERVICE) may read; any other
     * caller gets ACCT_002 (404) — no existence leak. Same non-cached guarded
     * pattern as AccountReadService.getBalanceForCaller.
     */
    public AccountResponse getByIdForCaller(UUID id, Jwt jwt) {
        Account account = accountRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AccountException(ERR_CODE_NOT_FOUND, ERR_ACCOUNT_NOT_FOUND));
        accessPolicy.requireReadAccess(account.getKeycloakUserId(), jwt);
        return toResponse(account);
    }

    public List<AccountResponse> getAccountsByStatus(Account.AccountStatus status) {
        return accountRepository.findByStatusAndDeletedAtIsNull(status)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
