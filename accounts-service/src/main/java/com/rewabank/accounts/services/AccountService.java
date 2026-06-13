package com.rewabank.accounts.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.accounts.client.CustomersFeignClient;
import com.rewabank.accounts.dto.AccountCreateRequest;
import com.rewabank.accounts.dto.AccountResponse;
import com.rewabank.accounts.dto.KycStatusResponse;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.entity.OutboxEvent;
import com.rewabank.accounts.exception.AccountException;
import com.rewabank.accounts.repository.AccountsRepository;
import com.rewabank.accounts.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final AccountsRepository      accountRepository;
    private final OutboxEventRepository  outboxEventRepository;
    private final CustomersFeignClient   customersFeignClient;
    private final AccountReadService     accountReadService;
    private final ObjectMapper           objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private final SecureRandom secureRandom = new SecureRandom();

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

        // Save outbox event in SAME transaction — guaranteed delivery
        saveOutboxEvent(savedAccount.getId().toString(),
                EVT_ACCOUNT_CREATED, TOPIC_ACCOUNT_CREATED,
                buildAccountPayload(savedAccount, EVT_ACCOUNT_CREATED));

        log.info("Account created: {} status: PENDING for customer: {}",
                accountNumber, customerId);

        return toResponse(savedAccount);
    }

    // ── Activate account (called after KYC verified) ──────────────────────────
    @Transactional
    public AccountResponse activateAccount(UUID accountId) {
        Account account = accountRepository
                .findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new AccountException("ACCT_002", "Account not found"));

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
        saveOutboxEvent(account.getId().toString(),
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
                .orElseThrow(() -> new AccountException("ACCT_002", "Account not found"));

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
        saveOutboxEvent(account.getId().toString(),
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
                .orElseThrow(() -> new AccountException("ACCT_002", "Account not found"));

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
        saveOutboxEvent(account.getId().toString(),
                EVT_BALANCE_UPDATED, TOPIC_BALANCE_UPDATED,
                buildBalancePayload(account, amount, "DEBIT",
                        previousBalance, correlationId));

        log.info("Debit ₹{} from account {} new balance: {}",
                amount, account.getAccountNumber(), account.getBalance());

        return toResponse(account);
    }

    // ── Freeze account ────────────────────────────────────────────────────────
    @Transactional
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    public AccountResponse freezeAccount(UUID accountId, String reason) {
        Account account = accountRepository
                .findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new AccountException("ACCT_002", "Account not found"));

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

        saveOutboxEvent(account.getId().toString(),
                EVT_ACCOUNT_FROZEN, TOPIC_ACCOUNT_FROZEN,
                buildAccountPayload(account, EVT_ACCOUNT_FROZEN));

        log.warn("Account frozen: {} reason: {}",
                account.getAccountNumber(), reason);
        return toResponse(account);
    }

    // ── Unfreeze account ──────────────────────────────────────────────────────
    @Transactional
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    public AccountResponse unfreezeAccount(UUID accountId) {
        Account account = accountRepository
                .findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new AccountException("ACCT_002", "Account not found"));

        if (!account.canUnfreeze()) {
            throw new AccountException("ACCT_003",
                    "Account cannot be unfrozen from state: " + account.getStatus());
        }

        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setFrozenAt(null);
        account.setFrozenReason(null);
        accountRepository.save(account);

        accountReadService.updateBalanceCache(account);

        saveOutboxEvent(account.getId().toString(),
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
                .orElseThrow(() -> new AccountException("ACCT_002", "Account not found"));

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

        saveOutboxEvent(account.getId().toString(),
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
                .orElseThrow(() -> new AccountException("ACCT_002", "Account not found"));

        if (account.getStatus() == Account.AccountStatus.ACTIVE) {
            account.setStatus(Account.AccountStatus.DORMANT);
            accountRepository.save(account);

            saveOutboxEvent(account.getId().toString(),
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

    private BigDecimal minimumBalanceFor(Account.AccountType type) {
        return switch (type) {
            case SAVINGS          -> new BigDecimal("1000.00");
            case CURRENT          -> new BigDecimal("10000.00");
            case SALARY           -> BigDecimal.ZERO;
            case FIXED_DEPOSIT,
                 RECURRING_DEPOSIT -> BigDecimal.ZERO;
        };
    }

    @Transactional
    public void saveOutboxEvent(String aggregateId, String eventType,
                                String topic, Map<String, Object> payload) {
        try {
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();
            outboxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload for {}: {}",
                    eventType, e.getMessage());
            throw new RuntimeException("Outbox serialization failed", e);
        }
    }

    private Map<String, Object> buildAccountPayload(Account a, String eventType) {
        return Map.of(
                "eventType",     eventType,
                "accountId",     a.getId().toString(),
                "accountNumber", a.getAccountNumber(),
                "customerId",    a.getCustomerId().toString(),
                "keycloakUserId",a.getKeycloakUserId(),
                "accountType",   a.getAccountType().name(),
                "status",        a.getStatus().name(),
                "currency",      a.getCurrency(),
                "occurredAt",    LocalDateTime.now().toString()
        );
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
                a.getIfscCode(), a.getActivatedAt(), a.getCreatedAt()
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
                .orElseThrow(() -> new AccountException("ACCT_002", "Account not found"));
    }
}
