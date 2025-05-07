package com.rewabank.Transaction.Service.Controller;

import com.rewabank.Transaction.Service.Entity.Transaction;
import com.rewabank.Transaction.Service.Service.TransactionService;
import com.rewabank.Transaction.Service.dto.TransactionRequestDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<Transaction> createTransaction(@RequestBody TransactionRequestDto transactionRequestDto) {
        Transaction createdTransaction = transactionService.createTransaction(transactionRequestDto);
        return ResponseEntity.ok(createdTransaction);
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<List<Transaction>> getTransactionsByAccountId(@PathVariable Long accountId) {
        List<Transaction> transactions = transactionService.getTransactionsByAccountId(accountId);
        return ResponseEntity.ok(transactions);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<Void> updateTransaction(@PathVariable Long id, @RequestBody Transaction transaction) {
        transaction.setId(id);
        transactionService.updateTransaction(transaction);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reverse/{transactionId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> reverseTransaction(@PathVariable Long transactionId, @RequestParam Long reversalTransactionId) {
        transactionService.reverseTransaction(transactionId, reversalTransactionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/status/{transactionId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> updateTransactionStatus(@PathVariable Long transactionId, @RequestParam String status) {
        transactionService.updateTransactionStatus(transactionId, status);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<List<Transaction>> getTransactionsByAccountId(@PathVariable Long accountId) {
        List<Transaction> transactions = transactionService.getTransactionsByAccountId(accountId);
        return ResponseEntity.ok(transactions);
    }
}
