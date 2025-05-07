package com.rewabank.Transaction.Service.Repository;

import com.rewabank.Transaction.Service.Entity.Transaction;
import com.rewabank.Transaction.Service.Utility.TransactionStatus;
import com.rewabank.Transaction.Service.Utility.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountId(Long accountId);

    List<Transaction> findByTransactionStatus(TransactionStatus status);

    List<Transaction> findByTransactionType(TransactionType type);

    List<Transaction> findByTransactionDateBetween(LocalDateTime startDate, LocalDateTime endDate);
}
