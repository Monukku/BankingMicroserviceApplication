package com.rewabank.customers.controller;

import com.rewabank.customers.dto.CustomerResponse;
import com.rewabank.customers.dto.KycStatusResponse;
import com.rewabank.customers.services.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer profile endpoints")
public class CustomerController {

    private final CustomerService customerService;

    // Get own profile
    @GetMapping("/me")
    @Operation(summary = "Get current customer profile")
    public ResponseEntity<CustomerResponse> getMyProfile(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                customerService.getByKeycloakUserId(jwt.getSubject()));
    }

    // Get by ID — staff only
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TELLER','RELATIONSHIP_MANAGER','BRANCH_MANAGER','AUDITOR','SUPER_ADMIN')")
    @Operation(summary = "Get customer profile by ID — staff only")
    public ResponseEntity<CustomerResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getById(id));
    }

    // KYC gate — called by Accounts MS internally (mTLS verified by Istio)
    @GetMapping("/{id}/kyc-status")
    @Operation(summary = "Get KYC status — used internally by Accounts MS")
    public ResponseEntity<KycStatusResponse> getKycStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getKycStatus(id));
    }
}
