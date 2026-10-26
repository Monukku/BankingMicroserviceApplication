package com.rewabank.accounts.controller;

import com.rewabank.accounts.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.services.AccountReadService;
import com.rewabank.accounts.services.AccountService;
import com.rewabank.accounts.services.DepositService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Accounts", description = "Account management endpoints")
public class AccountController {

    private final AccountService     accountService;
    private final AccountReadService accountReadService;
    private final DepositService     depositService;

    // Create account
    @PostMapping
    @Operation(summary = "Create a new bank account")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody AccountCreateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Customer-Id") UUID customerId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(
                        jwt.getSubject(), customerId, request));
    }

    // Get all accounts for current user
    @GetMapping("/my-accounts")
    @Operation(summary = "Get all accounts for current user")
    public ResponseEntity<List<AccountResponse>> getMyAccounts(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                accountService.getAccountsByUser(jwt.getSubject()));
    }

    // Get account by ID
    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID — owner, staff, or internal service only")
    public ResponseEntity<AccountResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(accountService.getByIdForCaller(id, jwt));
    }

    // Lookup by account number — used by transactions-service and frontend review screen
    @GetMapping("/lookup")
    @Operation(summary = "Lookup account by account number — for transfer recipient verification")
    public ResponseEntity<AccountLookupResponse> lookupByAccountNumber(
            @RequestParam @Size(min = 12, max = 12, message = "Account number must be 12 digits") String accountNumber) {
        return ResponseEntity.ok(accountService.lookupByAccountNumber(accountNumber));
    }

    // Get balance — served from Redis CQRS read side
    @GetMapping("/{accountNumber}/balance")
    @Operation(summary = "Get account balance (CQRS — Redis read)")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable String accountNumber,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                accountReadService.getBalanceForCaller(accountNumber, jwt));
    }

    // Get accounts by status — staff only (TELLER, BRANCH_MANAGER, SUPER_ADMIN)
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('TELLER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Get all accounts by status — staff only")
    public ResponseEntity<List<AccountResponse>> getByStatus(
            @PathVariable Account.AccountStatus status) {
        return ResponseEntity.ok(accountService.getAccountsByStatus(status));
    }

    // Activate account manually (staff)
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('TELLER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Activate account — staff only")
    public ResponseEntity<AccountResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.activateAccount(id));
    }

    // Reject pending account (teller)
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('TELLER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Reject a PENDING account — staff only")
    public ResponseEntity<AccountResponse> reject(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(accountService.rejectAccount(id, body.get("reason")));
    }

    // Freeze account
    @PatchMapping("/{id}/freeze")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Freeze account")
    public ResponseEntity<AccountResponse> freeze(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                accountService.freezeAccount(id, body.get("reason")));
    }

    // Unfreeze account
    @PatchMapping("/{id}/unfreeze")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Unfreeze account")
    public ResponseEntity<AccountResponse> unfreeze(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.unfreezeAccount(id));
    }

    // Close account
    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Close account permanently")
    public ResponseEntity<AccountResponse> close(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.closeAccount(id));
    }

    // ── Fixed Deposit ────────────────────────────────────────────────────────

    @PostMapping("/fixed-deposit")
    @Operation(summary = "Open a Fixed Deposit (funded from savings/current account)")
    public ResponseEntity<AccountResponse> openFixedDeposit(
            @Valid @RequestBody FixedDepositRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Customer-Id") UUID customerId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(depositService.openFixedDeposit(
                        jwt.getSubject(), customerId, request));
    }

    @GetMapping("/my-fixed-deposits")
    @Operation(summary = "Get my Fixed Deposits")
    public ResponseEntity<List<AccountResponse>> getMyFixedDeposits(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                depositService.getMyFixedDeposits(jwt.getSubject()));
    }

    // ── Recurring Deposit ────────────────────────────────────────────────────

    @PostMapping("/recurring-deposit")
    @Operation(summary = "Open a Recurring Deposit (monthly auto-debit from savings/current)")
    public ResponseEntity<AccountResponse> openRecurringDeposit(
            @Valid @RequestBody RecurringDepositRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Customer-Id") UUID customerId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(depositService.openRecurringDeposit(
                        jwt.getSubject(), customerId, request));
    }

    @GetMapping("/my-recurring-deposits")
    @Operation(summary = "Get my Recurring Deposits")
    public ResponseEntity<List<AccountResponse>> getMyRecurringDeposits(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                depositService.getMyRecurringDeposits(jwt.getSubject()));
    }

    // ── Salary Account (staff only) ──────────────────────────────────────────

    @PostMapping("/salary")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Open a Salary account for a customer — staff only")
    public ResponseEntity<AccountResponse> openSalaryAccount(
            @Valid @RequestBody SalaryAccountRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(depositService.openSalaryAccount(
                        jwt.getSubject(), request));
    }
}
