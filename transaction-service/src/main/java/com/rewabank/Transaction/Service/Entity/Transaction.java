package com.rewabank.Transaction.Service.Entity;

import com.rewabank.Transaction.Service.Utility.Currency;
import com.rewabank.Transaction.Service.Utility.TransactionStatus;
import com.rewabank.Transaction.Service.Utility.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private Double amount;
    private LocalDateTime transactionDate;
    private Long reversalTransactionId; // Assuming this is the field

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    private TransactionStatus transactionStatus;

    @Enumerated(EnumType.STRING)
    private Currency currency;

    private String description; // Optional: For details like "ATM Withdrawal" or "Payment to John"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(nullable = false)
    private Boolean isFraudulent = false; // Flag for fraud detection

    @Version
    private int version; // For optimistic locking
}

