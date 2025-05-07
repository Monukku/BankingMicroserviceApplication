package com.rewabank.Transaction.Service.Service;

import com.rewabank.Transaction.Service.Entity.Transaction;
import com.rewabank.Transaction.Service.Utility.TransactionStatus;
import com.rewabank.Transaction.Service.Utility.TransactionType;
import com.rewabank.Transaction.Service.dto.TransactionRequestDto;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionService {

    Transaction createTransaction(TransactionRequestDto transactionRequestDto);

    List<Transaction> getTransactionsByAccountId(Long accountId);

    public Transaction transactionFallback(Transaction transaction, Throwable throwable);

    List<Transaction> getTransactionsByStatus(TransactionStatus status);

    List<Transaction> getTransactionsByType(TransactionType type);

    List<Transaction> getTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    void updateTransaction(Transaction transaction);

    public void reverseTransaction(Long transactionId, Long reversalTransactionId);

    public void updateTransactionStatus(Long transactionId, String status);

    void deleteTransaction(Long id);
}


