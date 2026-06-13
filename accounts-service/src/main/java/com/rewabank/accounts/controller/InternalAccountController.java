package com.rewabank.accounts.controller;

import com.rewabank.accounts.dto.AccountDebitCreditRequest;
import com.rewabank.accounts.dto.AccountResponse;
import com.rewabank.accounts.services.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal endpoints — called by Transactions MS via Feign (mTLS verified by Istio).
 * NOT exposed via API Gateway — authz-policy restricts to transactions-ms-sa only.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts Internal", description = "Internal endpoints for MS-to-MS communication")
public class InternalAccountController {

    private final AccountService accountService;

    @PatchMapping("/{accountId}/debit")
    @Operation(summary = "Debit account — called by Transactions MS only")
    public ResponseEntity<AccountResponse> debit(
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountDebitCreditRequest request) {
        return ResponseEntity.ok(
                accountService.debit(accountId, request.amount(), request.correlationId()));
    }

    @PatchMapping("/{accountId}/credit")
    @Operation(summary = "Credit account — called by Transactions MS only")
    public ResponseEntity<AccountResponse> credit(
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountDebitCreditRequest request) {
        return ResponseEntity.ok(
                accountService.credit(accountId, request.amount(), request.correlationId()));
    }
}