package com.rewabank.accounts.services;

import com.rewabank.accounts.dto.BalanceResponse;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.exception.AccountException;
import com.rewabank.accounts.repository.AccountsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountReadServiceTest {

    @Mock
    private AccountsRepository accountsRepository;

    @InjectMocks
    private AccountReadService accountReadService;

    private Account activeAccount;

    @BeforeEach
    void setUp() {
        activeAccount = Account.builder()
                .accountNumber("123456789012")
                .balance(new BigDecimal("5000.00"))
                .minimumBalance(new BigDecimal("1000.00"))
                .currency("INR")
                .status(Account.AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void getBalance_ShouldReturnCorrectBalanceResponse_WhenAccountExists() {
        when(accountsRepository.findByAccountNumberAndDeletedAtIsNull("123456789012"))
                .thenReturn(Optional.of(activeAccount));

        BalanceResponse response = accountReadService.getBalance("123456789012");

        assertEquals("123456789012", response.accountNumber());
        assertEquals(new BigDecimal("5000.00"), response.balance());
        // availableBalance = balance - minimumBalance = 5000 - 1000 = 4000
        assertEquals(new BigDecimal("4000.00"), response.availableBalance());
        assertEquals("INR", response.currency());
        assertEquals("ACTIVE", response.status());
        assertNotNull(response.asOf());
    }

    @Test
    void getBalance_ShouldThrowAccountException_WhenAccountNotFound() {
        when(accountsRepository.findByAccountNumberAndDeletedAtIsNull("nonexistent"))
                .thenReturn(Optional.empty());

        AccountException ex = assertThrows(AccountException.class,
                () -> accountReadService.getBalance("nonexistent"));
        assertEquals("ACCT_002", ex.getErrorCode());
    }

    @Test
    void getBalance_ShouldReturnZeroAvailableBalance_WhenBalanceEqualsMinimum() {
        activeAccount.setBalance(new BigDecimal("1000.00"));
        activeAccount.setMinimumBalance(new BigDecimal("1000.00"));
        when(accountsRepository.findByAccountNumberAndDeletedAtIsNull("123456789012"))
                .thenReturn(Optional.of(activeAccount));

        BalanceResponse response = accountReadService.getBalance("123456789012");

        assertEquals(BigDecimal.ZERO, response.availableBalance().stripTrailingZeros());
    }

    @Test
    void updateBalanceCache_ShouldReturnBalanceResponseWithUpdatedValues() {
        BalanceResponse response = accountReadService.updateBalanceCache(activeAccount);

        assertEquals("123456789012", response.accountNumber());
        assertEquals(new BigDecimal("5000.00"), response.balance());
        assertEquals(new BigDecimal("4000.00"), response.availableBalance());
        assertEquals("INR", response.currency());
        assertEquals("ACTIVE", response.status());
        // No repository call — cache put is computed from the account directly
        verifyNoInteractions(accountsRepository);
    }

    @Test
    void updateBalanceCache_ShouldReflectSalaryAccountWithZeroMinimumBalance() {
        Account salaryAccount = Account.builder()
                .accountNumber("999999999999")
                .balance(new BigDecimal("25000.00"))
                .minimumBalance(BigDecimal.ZERO)
                .currency("INR")
                .status(Account.AccountStatus.ACTIVE)
                .build();

        BalanceResponse response = accountReadService.updateBalanceCache(salaryAccount);

        // For salary accounts, available == balance (no minimum balance requirement)
        assertEquals(new BigDecimal("25000.00"), response.availableBalance());
    }

    @Test
    void evictBalanceCache_ShouldCompleteWithoutException() {
        assertDoesNotThrow(() -> accountReadService.evictBalanceCache("123456789012"));
    }
}