package com.rewabank.accounts.controller;

import com.rewabank.accounts.dto.AccountCreateRequest;
import com.rewabank.accounts.dto.AccountResponse;
import com.rewabank.accounts.dto.BalanceResponse;
import com.rewabank.accounts.services.AccountReadService;
import com.rewabank.accounts.services.AccountService;
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
@Tag(name = "Accounts", description = "Account management endpoints")
public class AccountController {

    private final AccountService     accountService;
    private final AccountReadService accountReadService;

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
    @Operation(summary = "Get account by ID")
    public ResponseEntity<AccountResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getById(id));
    }

    // Get balance — served from Redis CQRS read side
    @GetMapping("/{accountNumber}/balance")
    @Operation(summary = "Get account balance (CQRS — Redis read)")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(
                accountReadService.getBalance(accountNumber));
    }

    // Activate account manually (staff)
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('TELLER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Activate account — staff only")
    public ResponseEntity<AccountResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.activateAccount(id));
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
}
