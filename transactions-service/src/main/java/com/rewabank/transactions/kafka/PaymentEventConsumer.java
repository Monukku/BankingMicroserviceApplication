package com.rewabank.transactions.kafka;

import com.rewabank.transactions.entity.LedgerEntry;
import com.rewabank.transactions.entity.Transaction;
import com.rewabank.transactions.repository.LedgerEntryRepository;
import com.rewabank.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @KafkaListener(
            topics           = "bank.payment.completed",
            groupId          = "${kafka.consumer.groups.payments}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onPaymentCompleted(Map<String, Object> event) {
        String paymentOrderId = (String) event.get("paymentOrderId");

        // Idempotent — skip if already recorded
        if (transactionRepository.findByIdempotencyKey(paymentOrderId).isPresent()) {
            log.debug("Deposit already recorded for paymentOrderId: {}", paymentOrderId);
            return;
        }

        String     keycloakUserId    = (String) event.get("keycloakUserId");
        UUID       accountId         = UUID.fromString((String) event.get("sourceAccountId"));
        String     accountNumber     = (String) event.get("sourceAccountNumber");
        BigDecimal amount            = new BigDecimal((String) event.get("amount"));
        String     currency          = (String) event.getOrDefault("currency", "INR");
        String     razorpayPaymentId = (String) event.get("razorpayPaymentId");
        String     paymentMethod     = (String) event.getOrDefault("paymentMethod", "UPI");

        // For a deposit: source is external (Razorpay), destination is the credited account
        Transaction tx = Transaction.builder()
                .idempotencyKey(paymentOrderId)
                .keycloakUserId(keycloakUserId)
                .sourceAccountId(accountId)
                .destinationAccountId(accountId)
                .sourceAccountNumber("EXTERNAL")
                .destinationAccountNumber(accountNumber)
                .amount(amount)
                .currency(currency)
                .transactionType(Transaction.TransactionType.DEPOSIT)
                .status(Transaction.TransactionStatus.COMPLETED)
                .remarks("Razorpay " + paymentMethod + " deposit")
                .referenceNumber(razorpayPaymentId)
                .debitCompleted(true)
                .creditCompleted(true)
                .completedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);

        // Double-entry ledger
        // DEBIT  → external/Razorpay nostro (money entering the bank)
        LedgerEntry debit = LedgerEntry.builder()
                .transactionId(tx.getId())
                .accountId(accountId)
                .accountNumber("EXTERNAL")
                .entryType(LedgerEntry.EntryType.DEBIT)
                .amount(amount)
                .currency(currency)
                .balanceAfter(BigDecimal.ZERO)
                .description("Razorpay deposit received - " + razorpayPaymentId)
                .build();

        // CREDIT → customer account (money arriving in customer account)
        LedgerEntry credit = LedgerEntry.builder()
                .transactionId(tx.getId())
                .accountId(accountId)
                .accountNumber(accountNumber)
                .entryType(LedgerEntry.EntryType.CREDIT)
                .amount(amount)
                .currency(currency)
                .balanceAfter(BigDecimal.ZERO)
                .description("Razorpay " + paymentMethod + " deposit - " + razorpayPaymentId)
                .build();

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);

        log.info("Deposit recorded — paymentOrderId: {} account: {} amount: ₹{}",
                paymentOrderId, accountNumber, amount);
        // Exceptions propagate to DefaultErrorHandler → retry with backoff → DLT after 3 attempts
    }
}

