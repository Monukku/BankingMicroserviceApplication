package com.rewabank.transactions.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.transactions.client.AccountsFeignClient;
import com.rewabank.transactions.client.FraudFeignClient;
import com.rewabank.transactions.dto.FraudScoreResponse;
import com.rewabank.transactions.dto.TransferRequest;
import com.rewabank.transactions.entity.Transaction;
import com.rewabank.transactions.exception.TransactionException;
import com.rewabank.transactions.repository.LedgerEntryRepository;
import com.rewabank.transactions.repository.OutboxEventRepository;
import com.rewabank.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferSagaTest {

    @Mock private FraudFeignClient      fraudClient;
    @Mock private AccountsFeignClient   accountsClient;
    @Mock private TransactionRepository transactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ObjectMapper          objectMapper;

    @InjectMocks private TransferSaga transferSaga;

    private UUID   sourceId;
    private UUID   destId;
    private Transaction txn;
    private TransferRequest request;

    @BeforeEach
    void setUp() throws Exception {
        sourceId = UUID.randomUUID();
        destId   = UUID.randomUUID();

        txn = Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey("idem-001")
                .keycloakUserId("kc-user-001")
                .sourceAccountId(sourceId)
                .destinationAccountId(destId)
                .sourceAccountNumber("111111111111")
                .destinationAccountNumber("222222222222")
                .amount(new BigDecimal("5000.00"))
                .transactionType(Transaction.TransactionType.TRANSFER)
                .status(Transaction.TransactionStatus.INITIATED)
                .build();

        request = new TransferRequest(
                sourceId, destId, "222222222222",
                new BigDecimal("5000.00"),
                Transaction.TransactionType.TRANSFER, "Test transfer", null);

        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void execute_ShouldComplete_WhenFraudApprovedAndBothAccountsSucceed() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));

        Transaction result = transferSaga.execute(txn, request);

        assertEquals(Transaction.TransactionStatus.COMPLETED, result.getStatus());
        assertTrue(result.getDebitCompleted());
        assertTrue(result.getCreditCompleted());
        assertNotNull(result.getCompletedAt());
        assertNotNull(result.getReferenceNumber());
    }

    @Test
    void execute_ShouldCreateTwoLedgerEntries_OnSuccess() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));

        transferSaga.execute(txn, request);

        verify(ledgerEntryRepository, times(2)).save(any());
    }

    @Test
    void execute_ShouldSaveOutboxEvent_OnSuccess() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));

        transferSaga.execute(txn, request);

        verify(outboxEventRepository, atLeastOnce()).save(any());
    }

    @Test
    void execute_ShouldRecordFraudScoreAndAction_OnTransaction() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 42, "APPROVE", ""));

        Transaction result = transferSaga.execute(txn, request);

        assertEquals(42, result.getFraudScore());
        assertEquals("APPROVE", result.getFraudAction());
    }

    @Test
    void execute_ShouldProceed_WhenFraudActionIsFlag() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 65, "FLAG", "High velocity"));

        Transaction result = transferSaga.execute(txn, request);

        // FLAG means proceed (flagged for review but not blocked)
        assertEquals(Transaction.TransactionStatus.COMPLETED, result.getStatus());
        assertEquals("FLAG", result.getFraudAction());
    }

    // ── Fraud BLOCK ───────────────────────────────────────────────────────────

    @Test
    void execute_ShouldThrow_WhenFraudBlocks() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(
                        sourceId.toString(), 90, "BLOCK", "Suspicious velocity"));

        TransactionException ex = assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));
        assertEquals("TXN_FRAUD_001", ex.getErrorCode());
    }

    @Test
    void execute_ShouldSetStatusBlocked_WhenFraudBlocks() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(
                        sourceId.toString(), 90, "BLOCK", "Suspicious velocity"));

        assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));

        assertEquals(Transaction.TransactionStatus.BLOCKED, txn.getStatus());
        assertNotNull(txn.getFailureReason());
    }

    @Test
    void execute_ShouldNotCallAccounts_WhenFraudBlocks() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(
                        sourceId.toString(), 90, "BLOCK", "Fraud"));

        assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));

        verifyNoInteractions(accountsClient);
    }

    // ── Debit failure ─────────────────────────────────────────────────────────

    @Test
    void execute_ShouldThrow_WhenDebitFails() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));
        doThrow(new RuntimeException("Insufficient balance"))
                .when(accountsClient).debitAccount(any(), any());

        TransactionException ex = assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));
        assertEquals("TXN_004", ex.getErrorCode());
    }

    @Test
    void execute_ShouldSetStatusFailed_WhenDebitFails() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));
        doThrow(new RuntimeException("Account frozen"))
                .when(accountsClient).debitAccount(any(), any());

        assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));

        assertEquals(Transaction.TransactionStatus.FAILED, txn.getStatus());
        assertNotNull(txn.getFailureReason());
    }

    @Test
    void execute_ShouldNotAttemptCredit_WhenDebitFails() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));
        doThrow(new RuntimeException("Debit failed"))
                .when(accountsClient).debitAccount(any(), any());

        assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));

        verify(accountsClient, never()).creditAccount(eq(destId), any());
    }

    // ── Credit failure → compensation ─────────────────────────────────────────

    @Test
    void execute_ShouldCompensate_WhenCreditFailsAfterDebitSucceeds() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));
        doNothing().when(accountsClient).debitAccount(any(), any());
        doThrow(new RuntimeException("Destination account frozen"))
                .when(accountsClient).creditAccount(eq(destId), any());

        TransactionException ex = assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));
        assertEquals("TXN_005", ex.getErrorCode());
    }

    @Test
    void execute_ShouldCreditBackToSource_WhenCreditFails() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));
        doNothing().when(accountsClient).debitAccount(any(), any());
        doThrow(new RuntimeException("Credit failed"))
                .when(accountsClient).creditAccount(eq(destId), any());

        assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));

        // Compensation: creditAccount called with source account ID
        verify(accountsClient).creditAccount(eq(sourceId), any());
    }

    @Test
    void execute_ShouldSetStatusReversed_WhenCompensationSucceeds() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));
        doNothing().when(accountsClient).debitAccount(any(), any());
        doThrow(new RuntimeException("Credit failed"))
                .when(accountsClient).creditAccount(eq(destId), any());
        doNothing().when(accountsClient).creditAccount(eq(sourceId), any());

        assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));

        assertEquals(Transaction.TransactionStatus.REVERSED, txn.getStatus());
        assertNotNull(txn.getReversalReason());
        assertNotNull(txn.getReversedAt());
    }

    // ── Ledger failure → full compensation ───────────────────────────────────

    @Test
    void execute_ShouldThrowTxn006_WhenLedgerCreationFails() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));
        doNothing().when(accountsClient).debitAccount(any(), any());
        doNothing().when(accountsClient).creditAccount(any(), any());
        doThrow(new RuntimeException("DB constraint violation"))
                .when(ledgerEntryRepository).save(any());

        TransactionException ex = assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));
        assertEquals("TXN_006", ex.getErrorCode());
    }

    @Test
    void execute_ShouldCompensateBothSides_WhenLedgerFails() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));
        doNothing().when(accountsClient).debitAccount(any(), any());
        doNothing().when(accountsClient).creditAccount(any(), any());
        doThrow(new RuntimeException("DB down")).when(ledgerEntryRepository).save(any());

        assertThrows(TransactionException.class, () -> transferSaga.execute(txn, request));

        // Compensation: debit destination (reverse credit) + credit source (reverse debit)
        verify(accountsClient).debitAccount(eq(destId), any());
        verify(accountsClient).creditAccount(eq(sourceId), any());
    }

    @Test
    void execute_ShouldStillThrowTxn006_WhenLedgerFailsAndCompensationAlsoFails() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));
        doNothing().when(accountsClient).debitAccount(eq(sourceId), any());
        // Credit to dest succeeds; compensation debit on dest fails; credit back to source fails
        doNothing().when(accountsClient).creditAccount(eq(destId), any());
        doThrow(new RuntimeException("Compensation failed"))
                .when(accountsClient).debitAccount(eq(destId), any());
        doThrow(new RuntimeException("Compensation failed"))
                .when(accountsClient).creditAccount(eq(sourceId), any());
        doThrow(new RuntimeException("DB down")).when(ledgerEntryRepository).save(any());

        // Exception must propagate even when compensation itself fails
        TransactionException ex = assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));
        assertEquals("TXN_006", ex.getErrorCode());
    }

    @Test
    void execute_ShouldSetStatusFailed_WhenBothCreditAndCompensationFail() {
        when(fraudClient.getFraudScore(any(), any(), any(), any()))
                .thenReturn(new FraudScoreResponse(sourceId.toString(), 10, "APPROVE", ""));
        doNothing().when(accountsClient).debitAccount(any(), any());
        // Both credit calls fail (to dest and compensation to source)
        doThrow(new RuntimeException("Network error"))
                .when(accountsClient).creditAccount(any(), any());

        assertThrows(TransactionException.class,
                () -> transferSaga.execute(txn, request));

        assertEquals(Transaction.TransactionStatus.FAILED, txn.getStatus());
        assertTrue(txn.getFailureReason().contains("CRITICAL"));
    }
}