package com.rewabank.transactions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.transactions.dto.TransferRequest;
import com.rewabank.transactions.dto.TransactionResponse;
import com.rewabank.transactions.entity.Transaction;
import com.rewabank.transactions.exception.TransactionException;
import com.rewabank.transactions.repository.BeneficiaryRepository;
import com.rewabank.transactions.repository.TransactionRepository;
import com.rewabank.transactions.saga.TransferSaga;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final IdempotencyService    idempotencyService;
    private final LimitService          limitService;
    private final TransferSaga          transferSaga;

    @Transactional
    public TransactionResponse transfer(String keycloakUserId,
                                        String idempotencyKey,
                                        String userRole,
                                        String ipAddress,
                                        TransferRequest request) {
        // ── Idempotency check ─────────────────────────────────────────────────
        var existing = idempotencyService.checkExisting(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate request — returning existing txn for key: {}",
                    idempotencyKey);
            return toResponse(existing.get());
        }

        // ── Mark in-progress ─────────────────────────────────────────────────
        idempotencyService.markInProgress(idempotencyKey);

        try {
            // ── Beneficiary cooling period check ─────────────────────────────
            beneficiaryRepository
                    .findByKeycloakUserIdAndBeneficiaryAccountNumberAndDeletedAtIsNull(
                            keycloakUserId, request.destinationAccountNumber())
                    .ifPresent(b -> {
                        if (!b.isCoolingPeriodOver()) {
                            throw new TransactionException("TXN_006",
                                    "Beneficiary in cooling period until: "
                                            + b.getCoolingPeriodEndsAt()
                                            + ". Transfers not allowed yet.");
                        }
                    });

            // ── Daily + per-transaction limit check ───────────────────────────
            limitService.validateAndConsumeLimit(
                    keycloakUserId, request.amount(), userRole);

            // ── Create transaction record ─────────────────────────────────────
            Transaction txn = Transaction.builder()
                    .idempotencyKey(idempotencyKey)
                    .keycloakUserId(keycloakUserId)
                    .sourceAccountId(request.sourceAccountId())
                    .destinationAccountId(request.destinationAccountId())
                    .sourceAccountNumber("")  // populated by Accounts MS response
                    .destinationAccountNumber(request.destinationAccountNumber())
                    .amount(request.amount())
                    .transactionType(request.transactionType())
                    .remarks(request.remarks())
                    .ipAddress(ipAddress)
                    .deviceId(request.deviceId())
                    .status(Transaction.TransactionStatus.INITIATED)
                    .build();

            transactionRepository.save(txn);

            // ── Execute Saga ──────────────────────────────────────────────────
            Transaction completed = transferSaga.execute(txn, request);

            // ── Mark idempotency key as completed ─────────────────────────────
            idempotencyService.markCompleted(
                    idempotencyKey, completed.getId().toString());

            return toResponse(completed);

        } catch (TransactionException e) {
            idempotencyService.release(idempotencyKey);
            throw e;
        } catch (Exception e) {
            idempotencyService.release(idempotencyKey);
            log.error("Unexpected error in transfer: {}", e.getMessage(), e);
            throw new TransactionException("TXN_500",
                    "Transaction failed due to an unexpected error");
        }
    }

    // Manual reversal — Branch Manager only
    @Transactional
    public TransactionResponse reverse(UUID transactionId,
                                       String reason,
                                       String keycloakUserId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionException(
                        "TXN_007", "Transaction not found"));

        if (txn.getStatus() != Transaction.TransactionStatus.COMPLETED) {
            throw new TransactionException("TXN_008",
                    "Only COMPLETED transactions can be reversed");
        }

        // Credit back to source (reverse the original debit)
        txn.setStatus(Transaction.TransactionStatus.REVERSED);
        txn.setReversalReason(reason);
        txn.setReversedAt(LocalDateTime.now());
        transactionRepository.save(txn);

        log.info("Transaction manually reversed: {} by: {}", transactionId, keycloakUserId);
        return toResponse(txn);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getMyTransactions(
            String keycloakUserId, Pageable pageable) {
        return transactionRepository
                .findByKeycloakUserIdOrderByCreatedAtDesc(keycloakUserId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id, String keycloakUserId) {
        return transactionRepository
                .findByIdAndUser(id, keycloakUserId)
                .map(this::toResponse)
                .orElseThrow(() -> new TransactionException(
                        "TXN_007", "Transaction not found"));
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getIdempotencyKey(),
                t.getSourceAccountNumber(), t.getDestinationAccountNumber(),
                t.getAmount(), t.getCurrency(),
                t.getTransactionType(), t.getStatus(),
                t.getRemarks(), t.getReferenceNumber(),
                t.getFraudAction(), t.getCompletedAt(), t.getCreatedAt()
        );
    }
}
