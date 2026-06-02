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
        // Arrange
        when(accountsRepository.findByIdAndDeletedAtIsNull(account.getId())).thenReturn(Optional.of(account));

        // Act
        AccountResponse response = accountService.getById(account.getId());

        // Assert
        assertEquals(account.getAccountNumber(), response.accountNumber());
    }
}
