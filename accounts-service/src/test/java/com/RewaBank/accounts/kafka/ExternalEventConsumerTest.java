package com.rewabank.accounts.kafka;

import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.repository.AccountsRepository;
import com.rewabank.accounts.services.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalEventConsumerTest {

    @Mock AccountService     accountService;
    @Mock AccountsRepository accountRepository;
    @InjectMocks ExternalEventConsumer consumer;

    // ── handleKycVerified ──────────────────────────────────────────────────────

    @Test
    void handleKycVerified_activatesPendingAccounts() {
        UUID customerId = UUID.randomUUID();
        UUID accountId  = UUID.randomUUID();
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(accountId);
        when(accountRepository.findPendingByCustomerId(customerId))
                .thenReturn(List.of(account));

        consumer.handleKycVerified(Map.of("customerId", customerId.toString()));

        verify(accountService).activateAccount(accountId);
    }

    @Test
    void handleKycVerified_noPendingAccounts_doesNotActivate() {
        UUID customerId = UUID.randomUUID();
        when(accountRepository.findPendingByCustomerId(customerId)).thenReturn(List.of());

        consumer.handleKycVerified(Map.of("customerId", customerId.toString()));

        verify(accountService, never()).activateAccount(any());
    }

    @Test
    void handleKycVerified_activationFails_propagatesAfterProcessingRemaining() {
        UUID customerId = UUID.randomUUID();
        Account a1 = mock(Account.class);
        Account a2 = mock(Account.class);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(a1.getId()).thenReturn(id1);
        when(a2.getId()).thenReturn(id2);
        when(accountRepository.findPendingByCustomerId(customerId))
                .thenReturn(List.of(a1, a2));
        doThrow(new RuntimeException("DB error")).when(accountService).activateAccount(id1);

        // The handler isolates per-account failures (so one bad account doesn't block the
        // others) but then rethrows an aggregate. Propagation is REQUIRED: it lets Spring
        // Kafka @RetryableTopic retry the event and, once retries are exhausted, route it to
        // the DLT. Swallowing here would silently drop the failed activation.
        assertThrows(RuntimeException.class,
                () -> consumer.handleKycVerified(Map.of("customerId", customerId.toString())));

        // Fail-isolation: the second account is still attempted before the aggregate is thrown.
        verify(accountService).activateAccount(id2);
    }

    // ── handleFraudAlert ───────────────────────────────────────────────────────

    @Test
    void handleFraudAlert_freezesAccountWithGivenReason() {
        UUID accountId = UUID.randomUUID();
        consumer.handleFraudAlert(Map.of(
                "accountId", accountId.toString(),
                "reason", "Suspicious large transfer"));
        verify(accountService).freezeAccount(accountId, "Suspicious large transfer");
    }

    @Test
    void handleFraudAlert_missingReason_usesDefaultMessage() {
        // Kills NegateConditionals on getOrDefault
        UUID accountId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("accountId", accountId.toString());

        consumer.handleFraudAlert(event);

        verify(accountService).freezeAccount(accountId, "Automatic freeze due to fraud alert");
    }

    @Test
    void handleFraudAlert_freezeFails_propagatesForRetry() {
        UUID accountId = UUID.randomUUID();
        doThrow(new RuntimeException("Freeze failed")).when(accountService)
                .freezeAccount(any(), any());

        // The handler has no try-catch by design: the exception must propagate so
        // @RetryableTopic retries the fraud event (then DLT). Swallowing would leave a
        // fraud-flagged account un-frozen.
        assertThrows(RuntimeException.class,
                () -> consumer.handleFraudAlert(Map.of("accountId", accountId.toString())));

        // No partial side effect on the failure path: only the freeze was attempted.
        verify(accountService).freezeAccount(eq(accountId), any());
        verifyNoMoreInteractions(accountService);
    }
}