package com.rewabank.transactions.service;

import com.rewabank.transactions.dto.TransactionResponse;
import com.rewabank.transactions.dto.TransferRequest;
import com.rewabank.transactions.entity.Beneficiary;
import com.rewabank.transactions.entity.Transaction;
import com.rewabank.transactions.exception.TransactionException;
import com.rewabank.transactions.repository.BeneficiaryRepository;
import com.rewabank.transactions.repository.TransactionRepository;
import com.rewabank.transactions.saga.TransferSaga;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private IdempotencyService    idempotencyService;
    @Mock private LimitService          limitService;
    @Mock private TransferSaga          transferSaga;

    @InjectMocks private TransactionService transactionService;

    private static final String USER_ID  = "kc-user-001";
    private static final String IDEM_KEY = "idem-key-001";

    private UUID          sourceId;
    private UUID          destId;
    private TransferRequest request;
    private Transaction   completedTxn;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
        destId   = UUID.randomUUID();

        request = new TransferRequest(
                sourceId, destId, "222222222222",
                new BigDecimal("5000.00"),
                Transaction.TransactionType.TRANSFER, "Rent payment", null);

        completedTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(IDEM_KEY)
                .keycloakUserId(USER_ID)
                .sourceAccountId(sourceId)
                .destinationAccountId(destId)
                .sourceAccountNumber("111111111111")
                .destinationAccountNumber("222222222222")
                .amount(new BigDecimal("5000.00"))
                .transactionType(Transaction.TransactionType.TRANSFER)
                .status(Transaction.TransactionStatus.COMPLETED)
                .referenceNumber("RWB1234567890")
                .completedAt(LocalDateTime.now())
                .build();

        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(beneficiaryRepository
                .findByKeycloakUserIdAndBeneficiaryAccountNumberAndDeletedAtIsNull(any(), any()))
                .thenReturn(Optional.empty());
        when(transferSaga.execute(any(), any())).thenReturn(completedTxn);
    }

    // ── transfer: happy path ──────────────────────────────────────────────────

    @Test
    void transfer_ShouldReturnResponse_WhenTransactionSucceeds() {
        when(idempotencyService.checkExisting(IDEM_KEY)).thenReturn(Optional.empty());

        TransactionResponse response = transactionService.transfer(
                USER_ID, IDEM_KEY, "CUSTOMER", "192.168.1.1", request);

        assertNotNull(response);
        assertEquals(Transaction.TransactionStatus.COMPLETED, response.status());
        assertEquals("RWB1234567890", response.referenceNumber());
    }

    @Test
    void transfer_ShouldCallSaga_WhenAllValidationsPassed() {
        when(idempotencyService.checkExisting(IDEM_KEY)).thenReturn(Optional.empty());

        transactionService.transfer(USER_ID, IDEM_KEY, "CUSTOMER", "10.0.0.1", request);

        verify(transferSaga).execute(any(Transaction.class), eq(request));
    }

    @Test
    void transfer_ShouldMarkIdempotencyCompleted_AfterSagaSuccess() {
        when(idempotencyService.checkExisting(IDEM_KEY)).thenReturn(Optional.empty());

        transactionService.transfer(USER_ID, IDEM_KEY, "CUSTOMER", "10.0.0.1", request);

        verify(idempotencyService).markCompleted(eq(IDEM_KEY), anyString());
    }

    @Test
    void transfer_ShouldValidateLimits_BeforeExecutingSaga() {
        when(idempotencyService.checkExisting(IDEM_KEY)).thenReturn(Optional.empty());

        transactionService.transfer(USER_ID, IDEM_KEY, "CUSTOMER", "10.0.0.1", request);

        verify(limitService).validateAndConsumeLimit(USER_ID,
                new BigDecimal("5000.00"), "CUSTOMER");
    }

    // ── transfer: idempotency ─────────────────────────────────────────────────

    @Test
    void transfer_ShouldReturnCachedResponse_WhenKeyAlreadyCompleted() {
        when(idempotencyService.checkExisting(IDEM_KEY))
                .thenReturn(Optional.of(completedTxn));

        TransactionResponse response = transactionService.transfer(
                USER_ID, IDEM_KEY, "CUSTOMER", "10.0.0.1", request);

        assertEquals(Transaction.TransactionStatus.COMPLETED, response.status());
        verifyNoInteractions(transferSaga);
        verifyNoInteractions(limitService);
    }

    @Test
    void transfer_ShouldThrow_WhenKeyIsInProgress() {
        when(idempotencyService.checkExisting(IDEM_KEY))
                .thenThrow(new TransactionException("TXN_001",
                        "Transaction already in progress"));

        TransactionException ex = assertThrows(TransactionException.class,
                () -> transactionService.transfer(
                        USER_ID, IDEM_KEY, "CUSTOMER", "10.0.0.1", request));
        assertEquals("TXN_001", ex.getErrorCode());
    }

    // ── transfer: beneficiary cooling period ─────────────────────────────────

    @Test
    void transfer_ShouldThrow_WhenBeneficiaryInCoolingPeriod() {
        when(idempotencyService.checkExisting(IDEM_KEY)).thenReturn(Optional.empty());

        Beneficiary coolBeneficiary = Beneficiary.builder()
                .keycloakUserId(USER_ID)
                .beneficiaryAccountNumber("222222222222")
                .coolingPeriodEndsAt(LocalDateTime.now().plusHours(12)) // still cooling
                .build();
        when(beneficiaryRepository
                .findByKeycloakUserIdAndBeneficiaryAccountNumberAndDeletedAtIsNull(
                        eq(USER_ID), eq("222222222222")))
                .thenReturn(Optional.of(coolBeneficiary));

        TransactionException ex = assertThrows(TransactionException.class,
                () -> transactionService.transfer(
                        USER_ID, IDEM_KEY, "CUSTOMER", "10.0.0.1", request));
        assertEquals("TXN_006", ex.getErrorCode());
        verifyNoInteractions(limitService);
        verifyNoInteractions(transferSaga);
    }

    @Test
    void transfer_ShouldProceed_WhenBeneficiaryCoolingPeriodOver() {
        when(idempotencyService.checkExisting(IDEM_KEY)).thenReturn(Optional.empty());

        Beneficiary cooledBeneficiary = Beneficiary.builder()
                .keycloakUserId(USER_ID)
                .beneficiaryAccountNumber("222222222222")
                .coolingPeriodEndsAt(LocalDateTime.now().minusHours(1)) // expired
                .build();
        when(beneficiaryRepository
                .findByKeycloakUserIdAndBeneficiaryAccountNumberAndDeletedAtIsNull(any(), any()))
                .thenReturn(Optional.of(cooledBeneficiary));

        assertDoesNotThrow(() -> transactionService.transfer(
                USER_ID, IDEM_KEY, "CUSTOMER", "10.0.0.1", request));
    }

    // ── transfer: error handling ──────────────────────────────────────────────

    @Test
    void transfer_ShouldReleaseIdempotencyKey_WhenTransactionExceptionThrown() {
        when(idempotencyService.checkExisting(IDEM_KEY)).thenReturn(Optional.empty());
        doThrow(new TransactionException("TXN_002", "Limit exceeded"))
                .when(limitService).validateAndConsumeLimit(any(), any(), any());

        assertThrows(TransactionException.class,
                () -> transactionService.transfer(
                        USER_ID, IDEM_KEY, "CUSTOMER", "10.0.0.1", request));

        verify(idempotencyService).release(IDEM_KEY);
    }

    @Test
    void transfer_ShouldReleaseIdempotencyKey_WhenUnexpectedExceptionThrown() {
        when(idempotencyService.checkExisting(IDEM_KEY)).thenReturn(Optional.empty());
        when(transferSaga.execute(any(), any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        TransactionException ex = assertThrows(TransactionException.class,
                () -> transactionService.transfer(
                        USER_ID, IDEM_KEY, "CUSTOMER", "10.0.0.1", request));
        assertEquals("TXN_500", ex.getErrorCode());
        verify(idempotencyService).release(IDEM_KEY);
    }

    // ── reverse ───────────────────────────────────────────────────────────────

    @Test
    void reverse_ShouldSetStatusReversed_WhenTransactionIsCompleted() {
        UUID txnId = completedTxn.getId();
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(completedTxn));

        TransactionResponse response = transactionService.reverse(
                txnId, "Customer request", USER_ID);

        assertEquals(Transaction.TransactionStatus.REVERSED, response.status());
        verify(transactionRepository).save(completedTxn);
    }

    @Test
    void reverse_ShouldSetReversalReason_OnReversal() {
        when(transactionRepository.findById(completedTxn.getId()))
                .thenReturn(Optional.of(completedTxn));

        transactionService.reverse(completedTxn.getId(), "Fraud confirmed", USER_ID);

        assertEquals("Fraud confirmed", completedTxn.getReversalReason());
        assertNotNull(completedTxn.getReversedAt());
    }

    @Test
    void reverse_ShouldThrow_WhenTransactionNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(transactionRepository.findById(unknownId)).thenReturn(Optional.empty());

        TransactionException ex = assertThrows(TransactionException.class,
                () -> transactionService.reverse(unknownId, "reason", USER_ID));
        assertEquals("TXN_007", ex.getErrorCode());
    }

    @Test
    void reverse_ShouldThrow_WhenTransactionNotCompleted() {
        completedTxn.setStatus(Transaction.TransactionStatus.FAILED);
        when(transactionRepository.findById(completedTxn.getId()))
                .thenReturn(Optional.of(completedTxn));

        TransactionException ex = assertThrows(TransactionException.class,
                () -> transactionService.reverse(completedTxn.getId(), "reason", USER_ID));
        assertEquals("TXN_008", ex.getErrorCode());
    }

    @Test
    void reverse_ShouldThrow_WhenTransactionIsAlreadyReversed() {
        completedTxn.setStatus(Transaction.TransactionStatus.REVERSED);
        when(transactionRepository.findById(completedTxn.getId()))
                .thenReturn(Optional.of(completedTxn));

        TransactionException ex = assertThrows(TransactionException.class,
                () -> transactionService.reverse(completedTxn.getId(), "reason", USER_ID));
        assertEquals("TXN_008", ex.getErrorCode());
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_ShouldReturnTransaction_WhenFoundForUser() {
        when(transactionRepository.findByIdAndUser(completedTxn.getId(), USER_ID))
                .thenReturn(Optional.of(completedTxn));

        TransactionResponse response = transactionService.getById(completedTxn.getId(), USER_ID);

        assertNotNull(response);
        assertEquals(completedTxn.getId(), response.id());
    }

    @Test
    void getById_ShouldThrow_WhenNotFoundForUser() {
        when(transactionRepository.findByIdAndUser(any(), any()))
                .thenReturn(Optional.empty());

        TransactionException ex = assertThrows(TransactionException.class,
                () -> transactionService.getById(UUID.randomUUID(), USER_ID));
        assertEquals("TXN_007", ex.getErrorCode());
    }

    // ── getMyTransactions ─────────────────────────────────────────────────────

    @Test
    void getMyTransactions_ShouldReturnPagedResults() {
        var pageable = PageRequest.of(0, 10);
        when(transactionRepository.findByKeycloakUserIdOrderByCreatedAtDesc(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(completedTxn)));

        Page<TransactionResponse> result =
                transactionService.getMyTransactions(USER_ID, pageable);

        assertEquals(1, result.getTotalElements());
        assertFalse(result.getContent().isEmpty(), "Result page must contain at least one element");
        assertEquals(Transaction.TransactionStatus.COMPLETED,
                result.getContent().get(0).status());
    }

    @Test
    void getMyTransactions_ShouldReturnEmptyPage_WhenNoTransactions() {
        var pageable = PageRequest.of(0, 10);
        when(transactionRepository.findByKeycloakUserIdOrderByCreatedAtDesc(USER_ID, pageable))
                .thenReturn(Page.empty());

        Page<TransactionResponse> result =
                transactionService.getMyTransactions(USER_ID, pageable);

        assertTrue(result.isEmpty());
    }
}