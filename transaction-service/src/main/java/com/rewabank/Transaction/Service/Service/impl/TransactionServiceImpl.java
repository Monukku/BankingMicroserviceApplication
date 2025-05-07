package com.rewabank.Transaction.Service.Service.impl;

import com.rewabank.Transaction.Service.Entity.Transaction;
import com.rewabank.Transaction.Service.Repository.TransactionRepository;
import com.rewabank.Transaction.Service.Service.TransactionService;
import com.rewabank.Transaction.Service.Utility.TransactionStatus;
import com.rewabank.Transaction.Service.Utility.TransactionType;
import com.rewabank.Transaction.Service.dto.TransactionRequestDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TransactionServiceImpl implements TransactionService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Transactional
    @Override
    public Transaction createTransaction(TransactionRequestDto transactionRequestDto) {
        Transaction transaction = new Transaction();
        transaction.setAccountId(transactionRequestDto.getAccountId());
        transaction.setAmount(transactionRequestDto.getAmount());
        transaction.setTransactionType(transactionRequestDto.getTransactionType());
        transaction.setCurrency(transactionRequestDto.getCurrency());
        transaction.setFees(transactionRequestDto.getFees());
        transaction.setExchangeRate(transactionRequestDto.getExchangeRate());
        transaction.setDescription(transactionRequestDto.getDescription());
        transaction.setTransactionStatus(TransactionStatus.PENDING); // Default status
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCreatedBy("SYSTEM");
        transaction.setUpdatedAt(LocalDateTime.now());
        transaction.setUpdatedBy("SYSTEM");

        // Additional logic for handling transfers
        if (transactionRequestDto.getFromAccountId() != null) {
            transaction.setFromAccountId(transactionRequestDto.getFromAccountId());
        }

        if (transactionRequestDto.getToAccountId() != null) {
            transaction.setToAccountId(transactionRequestDto.getToAccountId());
        }

        Transaction savedTransaction = transactionRepository.save(transaction);

        // Kafka event for eventual consistency
        publishTransactionEvent(savedTransaction);

        return savedTransaction;
    }


    @Transactional
    @Override
    public void reverseTransaction(Long transactionId, Long reversalTransactionId) {
        Optional<Transaction> originalTransactionOpt = transactionRepository.findById(transactionId);
        if (originalTransactionOpt.isPresent()) {
            Transaction originalTransaction = originalTransactionOpt.get();
            originalTransaction.setTransactionStatus(TransactionStatus.REVERSED);
            originalTransaction.setReversalTransactionId(reversalTransactionId);
            transactionRepository.save(originalTransaction);
        }
    }

    @Override
    public List<Transaction> getTransactionsByAccountId(Long accountId) {
        return transactionRepository.findByAccountId(accountId);
    }

    @Override
    public List<Transaction> getTransactionsByType(TransactionType type) {
        return null;
    }

    @Override
    public List<Transaction> getTransactionsByStatus(TransactionStatus status) {
        return transactionRepository.findByTransactionStatus(status);
    }

    @Override
    public List<Transaction> getTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.findByTransactionDateBetween(startDate, endDate);
    }

    @Transactional
    @Override
    public void updateTransaction(Transaction transaction) {
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
    }

    @Override
    public void updateTransactionStatus(Long transactionId, String status) {
        Optional<Transaction> transactionOpt = transactionRepository.findById(transactionId);
        if (transactionOpt.isPresent()) {
            Transaction transaction = transactionOpt.get();
            transaction.setTransactionStatus(TransactionStatus.valueOf(status));
            transactionRepository.save(transaction);
        }
    }

    @Transactional
    @Override
    public void deleteTransaction(Long id) {
        transactionRepository.deleteById(id);
    }

    private void publishTransactionEvent(Transaction transaction) {
        // Implement Kafka event publishing logic here
    }

    public Transaction transactionFallback(Transaction transaction, Throwable throwable) {
        // Fallback logic for failed transactions
        throw new RuntimeException("Transaction service is currently unavailable, please try again later.");
    }
}


