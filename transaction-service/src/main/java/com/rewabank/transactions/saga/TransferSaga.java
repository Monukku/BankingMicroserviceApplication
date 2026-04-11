package com.rewabank.transactions.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.transactions.client.AccountsFeignClient;
import com.rewabank.transactions.client.FraudFeignClient;
import com.rewabank.transactions.dto.AccountDebitCreditRequest;
import com.rewabank.transactions.dto.FraudScoreResponse;
import com.rewabank.transactions.dto.TransferRequest;
import com.rewabank.transactions.entity.LedgerEntry;
import com.rewabank.transactions.entity.OutboxEvent;
import com.rewabank.transactions.entity.Transaction;
import com.rewabank.transactions.exception.TransactionException;
import com.rewabank.transactions.repository.LedgerEntryRepository;
import com.rewabank.transactions.repository.OutboxEventRepository;
import com.rewabank.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Transfer Saga — coordinates:
 * Step 1: Fraud check (sync, 3s timeout)
 * Step 2: Debit source account (sync)
 * Step 3: Credit destination account (sync)
 * Step 4: Create ledger entries (local DB)
 * Step 5: Save outbox events (local DB, same transaction as Step 4)
 *
 * Compensation:
 * If Step 3 fails → reverse Step 2 (credit back source)
 * If Step 2 fails → no compensation needed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferSaga {

    private final FraudFeignClient      fraudClient;
    private final AccountsFeignClient   accountsClient;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper          objectMapper;

    private static final String TOPIC_INITIATED  = "bank.transaction.initiated";
    private static final String TOPIC_COMPLETED  = "bank.transaction.completed";
    private static final String TOPIC_FAILED     = "bank.transaction.failed";
    private static final String TOPIC_REVERSED   = "bank.transaction.reversed";

    @Transactional
    public Transaction execute(Transaction transaction,
                               TransferRequest request) {
        log.info("Saga START — txn: {} amount: ₹{}",
                transaction.getId(), transaction.getAmount());

        // ── Step 1: Fraud check ──────────────────────────────────────────────
        transaction.setStatus(Transaction.TransactionStatus.FRAUD_CHECK);
        transactionRepository.save(transaction);

        FraudScoreResponse fraud = fraudClient.getFraudScore(
                transaction.getSourceAccountId(),
                transaction.getAmount(),
                transaction.getTransactionType().name(),
                transaction.getId().toString()
        );

        transaction.setFraudScore(fraud.score());
        transaction.setFraudAction(fraud.action());

        if ("BLOCK".equals(fraud.action())) {
            transaction.setStatus(Transaction.TransactionStatus.BLOCKED);
            transaction.setFailureReason("Blocked by fraud system: " + fraud.reason());
            transactionRepository.save(transaction);
            saveOutbox(transaction, "TRANSACTION_BLOCKED", TOPIC_FAILED);
            log.warn("Transaction BLOCKED by fraud — txn: {} score: {}",
                    transaction.getId(), fraud.score());
            throw new TransactionException("TXN_FRAUD_001",
                    "Transaction blocked by fraud detection system");
        }

        if ("FLAG".equals(fraud.action())) {
            log.warn("Transaction FLAGGED by fraud — proceeding with manual review flag. txn: {}",
                    transaction.getId());
            // Flagged transactions proceed but are marked for review
        }

        // ── Step 2: Debit source account ─────────────────────────────────────
        transaction.setStatus(Transaction.TransactionStatus.PROCESSING);
        transactionRepository.save(transaction);

        try {
            accountsClient.debitAccount(
                    transaction.getSourceAccountId(),
                    new AccountDebitCreditRequest(
                            transaction.getSourceAccountId(),
                            transaction.getAmount(),
                            transaction.getId().toString()
                    )
            );
            transaction.setDebitCompleted(true);
            transactionRepository.save(transaction);
            log.info("Debit completed — txn: {}", transaction.getId());
        } catch (Exception e) {
            // Debit failed — no compensation needed, mark failed
            transaction.setStatus(Transaction.TransactionStatus.FAILED);
            transaction.setFailureReason("Debit failed: " + e.getMessage());
            transactionRepository.save(transaction);
            saveOutbox(transaction, "TRANSACTION_FAILED", TOPIC_FAILED);
            log.error("Debit FAILED — txn: {} error: {}",
                    transaction.getId(), e.getMessage());
            throw new TransactionException("TXN_004",
                    "Debit failed: " + e.getMessage());
        }

        // ── Step 3: Credit destination account ───────────────────────────────
        try {
            accountsClient.creditAccount(
                    transaction.getDestinationAccountId(),
                    new AccountDebitCreditRequest(
                            transaction.getDestinationAccountId(),
                            transaction.getAmount(),
                            transaction.getId().toString()
                    )
            );
            transaction.setCreditCompleted(true);
            log.info("Credit completed — txn: {}", transaction.getId());
        } catch (Exception e) {
            // Credit failed — COMPENSATE: reverse the debit
            log.error("Credit FAILED — compensating debit for txn: {}",
                    transaction.getId());
            compensateDebit(transaction);
            throw new TransactionException("TXN_005",
                    "Credit failed, debit has been reversed: " + e.getMessage());
        }

        // ── Step 4: Create ledger entries (double-entry) ──────────────────────
        createLedgerEntries(transaction);

        // ── Step 5: Mark completed + save outbox ─────────────────────────────
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setReferenceNumber(generateReferenceNumber());
        transactionRepository.save(transaction);

        saveOutbox(transaction, "TRANSACTION_COMPLETED", TOPIC_COMPLETED);

        log.info("Saga COMPLETE — txn: {} ref: {}",
                transaction.getId(), transaction.getReferenceNumber());

        return transaction;
    }

    // Compensation — reverse debit if credit fails
    private void compensateDebit(Transaction transaction) {
        try {
            accountsClient.creditAccount(
                    transaction.getSourceAccountId(),
                    new AccountDebitCreditRequest(
                            transaction.getSourceAccountId(),
                            transaction.getAmount(),
                            transaction.getId().toString()
                    )
            );
            transaction.setStatus(Transaction.TransactionStatus.REVERSED);
            transaction.setReversalReason("Automatic reversal — credit failed");
            transaction.setReversedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            saveOutbox(transaction, "TRANSACTION_REVERSED", TOPIC_REVERSED);
            log.info("Debit compensated (reversed) for txn: {}",
                    transaction.getId());
        } catch (Exception e) {
            // Compensation also failed — critical alert needed
            transaction.setStatus(Transaction.TransactionStatus.FAILED);
            transaction.setFailureReason(
                    "CRITICAL: Debit completed but credit AND compensation failed. " +
                            "Manual intervention required. Error: " + e.getMessage());
            transactionRepository.save(transaction);
            log.error("CRITICAL: Compensation failed for txn: {} — manual intervention required",
                    transaction.getId());
        }
    }

    private void createLedgerEntries(Transaction txn) {
        // Debit entry — source account
        LedgerEntry debit = LedgerEntry.builder()
                .transactionId(txn.getId())
                .accountId(txn.getSourceAccountId())
                .accountNumber(txn.getSourceAccountNumber())
                .entryType(LedgerEntry.EntryType.DEBIT)
                .amount(txn.getAmount())
                .balanceAfter(BigDecimal.ZERO)  // Accounts MS owns actual balance
                .description("Transfer to " + txn.getDestinationAccountNumber()
                        + " - " + (txn.getRemarks() != null ? txn.getRemarks() : ""))
                .build();

        // Credit entry — destination account
        LedgerEntry credit = LedgerEntry.builder()
                .transactionId(txn.getId())
                .accountId(txn.getDestinationAccountId())
                .accountNumber(txn.getDestinationAccountNumber())
                .entryType(LedgerEntry.EntryType.CREDIT)
                .amount(txn.getAmount())
                .balanceAfter(BigDecimal.ZERO)
                .description("Transfer from " + txn.getSourceAccountNumber()
                        + " - " + (txn.getRemarks() != null ? txn.getRemarks() : ""))
                .build();

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);
        log.debug("Ledger entries created for txn: {}", txn.getId());
    }

    private void saveOutbox(Transaction txn, String eventType, String topic) {
        try {
            Map<String, Object> payload = Map.of(
                    "eventId",                UUID.randomUUID().toString(),
                    "eventType",              eventType,
                    "transactionId",          txn.getId().toString(),
                    "sourceAccountId",        txn.getSourceAccountId().toString(),
                    "destinationAccountId",   txn.getDestinationAccountId().toString(),
                    "sourceAccountNumber",    txn.getSourceAccountNumber(),
                    "destinationAccountNumber", txn.getDestinationAccountNumber(),
                    "amount",                 txn.getAmount().toString(),
                    "status",                 txn.getStatus().name(),
                    "occurredAt",             LocalDateTime.now().toString()
            );

            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateId(txn.getId().toString())
                    .eventType(eventType)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload: {}", e.getMessage());
        }
    }

    private String generateReferenceNumber() {
        return "RWB" + System.currentTimeMillis();
    }
}
