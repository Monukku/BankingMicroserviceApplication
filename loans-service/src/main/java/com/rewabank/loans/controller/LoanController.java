package com.rewabank.loans.controller;

import com.rewabank.loans.dto.*;
import com.rewabank.loans.entity.LoanApplication;
import com.rewabank.loans.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loans", description = "Loan application endpoints")
public class LoanController {

    private final LoanService loanService;

    @PostMapping
    @Operation(summary = "Apply for a loan")
    public ResponseEntity<LoanApplicationResponse> apply(
            @Valid @RequestBody LoanApplicationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanService.apply(jwt.getSubject(), request));
    }

    @GetMapping("/my-loans")
    @Operation(summary = "Get my loan applications")
    public ResponseEntity<List<LoanApplicationResponse>> getMyLoans(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                loanService.getMyLoans(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get loan by ID")
    public ResponseEntity<LoanApplicationResponse> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(loanService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CREDIT_OFFICER','BRANCH_MANAGER'," +
            "'AUDITOR','SUPER_ADMIN')")
    @Operation(summary = "Get all loans — staff only")
    public ResponseEntity<Page<LoanApplicationResponse>> getAllLoans(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(loanService.getAllLoans(pageable));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('CREDIT_OFFICER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Get loans by status")
    public ResponseEntity<Page<LoanApplicationResponse>> getByStatus(
            @PathVariable LoanApplication.LoanStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(loanService.getByStatus(status, pageable));
    }

    @PatchMapping("/{id}/review/start")
    @PreAuthorize("hasAnyRole('CREDIT_OFFICER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Start loan review")
    public ResponseEntity<LoanApplicationResponse> startReview(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                loanService.startReview(id, jwt.getSubject()));
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('CREDIT_OFFICER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Approve or reject loan")
    public ResponseEntity<LoanApplicationResponse> review(
            @PathVariable UUID id,
            @Valid @RequestBody LoanReviewRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String role = extractPrimaryRole(jwt);
        return ResponseEntity.ok(
                loanService.review(id, jwt.getSubject(), role, request));
    }

    @PatchMapping("/{id}/disburse")
    @PreAuthorize("hasAnyRole('CREDIT_OFFICER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Disburse approved loan")
    public ResponseEntity<LoanApplicationResponse> disburse(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                loanService.disburse(id, jwt.getSubject()));
    }

    private String extractPrimaryRole(Jwt jwt) {
        try {
            var realmAccess = (java.util.Map<?, ?>) jwt.getClaims()
                    .get("realm_access");
            if (realmAccess == null) return "CUSTOMER";
            var roles = (java.util.List<?>) realmAccess.get("roles");
            for (String r : new String[]{
                    "SUPER_ADMIN", "BRANCH_MANAGER", "CREDIT_OFFICER"}) {
                if (roles.contains(r)) return r;
            }
            return "CUSTOMER";
        } catch (Exception e) {
            return "CUSTOMER";
        }
    }
}