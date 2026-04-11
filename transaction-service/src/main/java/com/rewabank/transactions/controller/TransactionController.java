package com.rewabank.transactions.controller;

import com.rewabank.transactions.dto.TransactionResponse;
import com.rewabank.transactions.dto.TransferRequest;
import com.rewabank.transactions.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transfer and transaction endpoints")
public class TransactionController {

    private final TransactionService transactionService;

    // Transfer — requires X-Idempotency-Key header
    @PostMapping("/transfer")
    @Operation(summary = "Initiate a money transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {

        String userRole = extractPrimaryRole(jwt);
        String ipAddress = httpRequest.getRemoteAddr();

        return ResponseEntity.status(HttpStatus.CREATED).body(
                transactionService.transfer(
                        jwt.getSubject(),
                        idempotencyKey,
                        userRole,
                        ipAddress,
                        request
                )
        );
    }

    // Get transaction history — paginated
    @GetMapping
    @Operation(summary = "Get my transaction history")
    public ResponseEntity<Page<TransactionResponse>> getMyTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(
                transactionService.getMyTransactions(jwt.getSubject(), pageable));
    }

    // Get transaction by ID
    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<TransactionResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                transactionService.getById(id, jwt.getSubject()));
    }

    // Manual reversal — Branch Manager only
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Reverse a completed transaction")
    public ResponseEntity<TransactionResponse> reverse(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                transactionService.reverse(id, body.get("reason"), jwt.getSubject()));
    }

    private String extractPrimaryRole(Jwt jwt) {
        try {
            var realmAccess = (java.util.Map<?, ?>) jwt.getClaims().get("realm_access");
            if (realmAccess == null) return "CUSTOMER";
            var roles = (java.util.List<?>) realmAccess.get("roles");
            if (roles == null || roles.isEmpty()) return "CUSTOMER";
            // Return highest privilege role
            for (String r : new String[]{"SUPER_ADMIN","BRANCH_MANAGER",
                    "CREDIT_OFFICER","RELATIONSHIP_MANAGER","TELLER"}) {
                if (roles.contains(r)) return r;
            }
            return "CUSTOMER";
        } catch (Exception e) {
            return "CUSTOMER";
        }
    }
}
