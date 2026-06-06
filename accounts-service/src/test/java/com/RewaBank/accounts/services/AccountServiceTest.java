package com.rewabank.accounts.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.accounts.client.CustomersFeignClient;
import com.rewabank.accounts.dto.*;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.exception.AccountException;
import com.rewabank.accounts.repository.AccountsRepository;
import com.rewabank.accounts.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CustomersFeignClient customersFeignClient;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private AccountReadService accountReadService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Spy
    @InjectMocks
    private AccountService accountService;

    private String keycloakUserId;
    private UUID customerId;
    private AccountCreateRequest createRequest;
    private Account account;

    @BeforeEach
    void setUp() {
        keycloakUserId = "test-user-123";
        customerId = UUID.randomUUID();
        createRequest = new AccountCreateRequest(Account.AccountType.SAVINGS, "BR001", "IFSC001");

        account = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("123456789012")
                .keycloakUserId(keycloakUserId)
                .customerId(customerId)
                .accountType(Account.AccountType.SAVINGS)
                .balance(BigDecimal.ZERO)
                .status(Account.AccountStatus.PENDING)
                .branchCode("BR001")
                .ifscCode("IFSC001")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createAccount_ShouldCreateAccountSuccessfully() throws JsonProcessingException {
        // Arrange
        when(accountsRepository.findByCustomerIdAndDeletedAtIsNull(customerId))
                .thenReturn(List.of());
        
        doReturn("123456789012").when(accountService).generateAccountNumber();
        
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{}");
        
        Account savedAccount = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("123456789012")
                .keycloakUserId(keycloakUserId)
                .customerId(customerId)
                .accountType(Account.AccountType.SAVINGS)
                .balance(BigDecimal.ZERO)
                .status(Account.AccountStatus.PENDING)
                .branchCode("BR001")
                .ifscCode("IFSC001")
                .createdAt(LocalDateTime.now())
                .build();
        
        when(accountsRepository.save(any(Account.class)))
                .thenReturn(savedAccount);

        // Act
        AccountResponse response = accountService.createAccount(keycloakUserId, customerId, createRequest);

        // Assert
        assertNotNull(response);
        assertEquals(Account.AccountType.SAVINGS, response.accountType());
        assertEquals("123456789012", response.accountNumber());
        verify(accountsRepository).save(any(Account.class));
        verify(outboxEventRepository).save(any());
    }

    @Test
    void createAccount_ShouldThrowException_WhenCustomerAlreadyHasAccount() {
        // Arrange
        when(accountsRepository.findByCustomerIdAndDeletedAtIsNull(customerId))
                .thenReturn(List.of(account));

        // Act & Assert
        AccountException exception = assertThrows(AccountException.class,
                () -> accountService.createAccount(keycloakUserId, customerId, createRequest));
        assertEquals("Active account of type SAVINGS already exists", exception.getMessage());
    }

    @Test
    void activateAccount_ShouldActivateAccountSuccessfully() throws AccountException, JsonProcessingException {
        // Arrange
        account.setStatus(Account.AccountStatus.PENDING);
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId())).thenReturn(Optional.of(account));
        when(customersFeignClient.getKycStatus(account.getCustomerId())).thenReturn(new KycStatusResponse(account.getCustomerId().toString(), account.getKeycloakUserId(), "VERIFIED", true, "KYC verified successfully"));
        doReturn("{}").when(objectMapper).writeValueAsString(any());

        // Act
        AccountResponse response = accountService.activateAccount(account.getId());

        // Assert
        assertEquals(Account.AccountStatus.ACTIVE, response.status());
        verify(accountsRepository).save(account);
    }

    @Test
    void activateAccount_ShouldThrowException_WhenAccountNotFound() {
        // Arrange
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId())).thenReturn(Optional.empty());

        // Act & Assert
        AccountException exception = assertThrows(AccountException.class,
                () -> accountService.activateAccount(account.getId()));
        assertEquals("Account not found", exception.getMessage());
    }

    @Test
    void freezeAccount_ShouldFreezeSuccessfully() throws AccountException {
        // Arrange
        account.setStatus(Account.AccountStatus.ACTIVE);
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId())).thenReturn(Optional.of(account));

        // Act
        AccountResponse response = accountService.freezeAccount(account.getId(), "Suspicious activity");

        // Assert
        assertEquals(Account.AccountStatus.FROZEN, response.status());
        verify(accountsRepository).save(account);
    }

    @Test
    void unfreezeAccount_ShouldUnfreezeSuccessfully() throws AccountException {
        // Arrange
        account.setStatus(Account.AccountStatus.FROZEN);
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId())).thenReturn(Optional.of(account));

        // Act
        AccountResponse response = accountService.unfreezeAccount(account.getId());

        // Assert
        assertEquals(Account.AccountStatus.ACTIVE, response.status());
        verify(accountsRepository).save(account);
    }

    @Test
    void closeAccount_ShouldCloseSuccessfully() throws AccountException {
        // Arrange
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.ZERO);
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId())).thenReturn(Optional.of(account));

        // Act
        AccountResponse response = accountService.closeAccount(account.getId());

        // Assert
        assertEquals(Account.AccountStatus.CLOSED, response.status());
        verify(accountsRepository).save(account);
    }

    @Test
    void closeAccount_ShouldThrowException_WhenBalanceNotZero() {
        // Arrange
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.valueOf(100));
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId())).thenReturn(Optional.of(account));

        // Act & Assert
        AccountException exception = assertThrows(AccountException.class,
                () -> accountService.closeAccount(account.getId()));
        assertEquals("Cannot close account with positive balance: ₹100", exception.getMessage());
    }

    @Test
    void getById_ShouldReturnAccountSuccessfully() throws AccountException {
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId())).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getById(account.getId());

        assertEquals(account.getAccountNumber(), response.accountNumber());
    }

    // ── credit() ──────────────────────────────────────────────────────────────

    @Test
    void credit_ShouldCreditSuccessfully() throws Exception {
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal("1000.00"));
        when(accountsRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        AccountResponse response = accountService.credit(account.getId(),
                new BigDecimal("500.00"), "corr-001");

        assertEquals(new BigDecimal("1500.00"), response.balance());
        verify(accountsRepository).save(account);
        verify(accountReadService).updateBalanceCache(account);
        verify(outboxEventRepository).save(any());
    }

    @Test
    void credit_ShouldThrowException_WhenAmountIsZero() {
        assertThrows(AccountException.class,
                () -> accountService.credit(account.getId(), BigDecimal.ZERO, "corr-002"));
    }

    @Test
    void credit_ShouldThrowException_WhenAmountIsNegative() {
        assertThrows(AccountException.class,
                () -> accountService.credit(account.getId(), new BigDecimal("-100"), "corr-003"));
    }

    @Test
    void credit_ShouldThrowException_WhenAccountNotFound() {
        when(accountsRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.empty());

        assertThrows(AccountException.class,
                () -> accountService.credit(account.getId(), new BigDecimal("100"), "corr-004"));
    }

    @Test
    void credit_ShouldThrowException_WhenAccountNotActive() {
        account.setStatus(Account.AccountStatus.FROZEN);
        when(accountsRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.credit(account.getId(), new BigDecimal("100"), "corr-005"));
        assertEquals("ACCT_006", ex.getErrorCode());
    }

    // ── debit() ───────────────────────────────────────────────────────────────

    @Test
    void debit_ShouldDebitSuccessfully() throws Exception {
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal("5000.00"));
        account.setMinimumBalance(new BigDecimal("1000.00"));
        when(accountsRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        AccountResponse response = accountService.debit(account.getId(),
                new BigDecimal("2000.00"), "corr-010");

        assertEquals(new BigDecimal("3000.00"), response.balance());
        verify(accountsRepository).save(account);
        verify(accountReadService).updateBalanceCache(account);
        verify(outboxEventRepository).save(any());
    }

    @Test
    void debit_ShouldThrowException_WhenAmountIsNegative() {
        assertThrows(AccountException.class,
                () -> accountService.debit(account.getId(), new BigDecimal("-50"), "corr-011"));
    }

    @Test
    void debit_ShouldThrowException_WhenAccountNotFound() {
        when(accountsRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.empty());

        assertThrows(AccountException.class,
                () -> accountService.debit(account.getId(), new BigDecimal("100"), "corr-012"));
    }

    @Test
    void debit_ShouldThrowException_WhenAccountNotActive() {
        account.setStatus(Account.AccountStatus.DORMANT);
        when(accountsRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.debit(account.getId(), new BigDecimal("100"), "corr-013"));
        assertEquals("ACCT_006", ex.getErrorCode());
    }

    @Test
    void debit_ShouldThrowException_WhenInsufficientBalance() {
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal("1000.00"));
        account.setMinimumBalance(new BigDecimal("1000.00")); // available = 0
        when(accountsRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.debit(account.getId(), new BigDecimal("500.00"), "corr-014"));
        assertEquals("ACCT_007", ex.getErrorCode());
    }

    @Test
    void debit_ShouldThrowException_WhenDebitExceedsAvailableBalance() {
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal("2000.00"));
        account.setMinimumBalance(new BigDecimal("1000.00")); // available = 1000
        when(accountsRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

        assertThrows(AccountException.class,
                () -> accountService.debit(account.getId(), new BigDecimal("1500.00"), "corr-015"));
    }

    // ── markDormant() ─────────────────────────────────────────────────────────

    @Test
    void markDormant_ShouldMarkDormant_WhenAccountIsActive() throws Exception {
        account.setStatus(Account.AccountStatus.ACTIVE);
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId()))
                .thenReturn(Optional.of(account));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        accountService.markDormant(account.getId());

        assertEquals(Account.AccountStatus.DORMANT, account.getStatus());
        verify(accountsRepository).save(account);
        verify(outboxEventRepository).save(any());
    }

    @Test
    void markDormant_ShouldNotMarkDormant_WhenAccountIsAlreadyFrozen() {
        account.setStatus(Account.AccountStatus.FROZEN);
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId()))
                .thenReturn(Optional.of(account));

        accountService.markDormant(account.getId());

        assertEquals(Account.AccountStatus.FROZEN, account.getStatus());
        verify(accountsRepository, never()).save(any());
    }

    @Test
    void markDormant_ShouldThrowException_WhenAccountNotFound() {
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId()))
                .thenReturn(Optional.empty());

        assertThrows(AccountException.class,
                () -> accountService.markDormant(account.getId()));
    }

    // ── minimumBalance by account type ────────────────────────────────────────

    @Test
    void createAccount_ShouldSetCorrectMinimumBalance_ForCurrentAccount() throws Exception {
        AccountCreateRequest currentRequest =
                new AccountCreateRequest(Account.AccountType.CURRENT, "BR001", "IFSC001");
        when(accountsRepository.findByCustomerIdAndDeletedAtIsNull(customerId))
                .thenReturn(List.of());
        doReturn("123456789013").when(accountService).generateAccountNumber();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Account savedAccount = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("123456789013")
                .keycloakUserId(keycloakUserId)
                .customerId(customerId)
                .accountType(Account.AccountType.CURRENT)
                .balance(BigDecimal.ZERO)
                .minimumBalance(new BigDecimal("10000.00"))
                .status(Account.AccountStatus.PENDING)
                .build();
        when(accountsRepository.save(any(Account.class))).thenReturn(savedAccount);

        AccountResponse response = accountService.createAccount(keycloakUserId, customerId, currentRequest);

        assertEquals(Account.AccountType.CURRENT, response.accountType());
    }

    @Test
    void activateAccount_ShouldThrowException_WhenKycNotVerified() throws Exception {
        account.setStatus(Account.AccountStatus.PENDING);
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId()))
                .thenReturn(Optional.of(account));
        when(customersFeignClient.getKycStatus(account.getCustomerId()))
                .thenReturn(new KycStatusResponse(account.getCustomerId().toString(),
                        account.getKeycloakUserId(), "PENDING", false, "KYC pending"));

        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.activateAccount(account.getId()));
        assertEquals("ACCT_004", ex.getErrorCode());
    }

    @Test
    void activateAccount_ShouldThrowException_WhenAccountNotPending() throws Exception {
        account.setStatus(Account.AccountStatus.ACTIVE);
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId()))
                .thenReturn(Optional.of(account));

        AccountException ex = assertThrows(AccountException.class,
                () -> accountService.activateAccount(account.getId()));
        assertEquals("ACCT_003", ex.getErrorCode());
    }
}
