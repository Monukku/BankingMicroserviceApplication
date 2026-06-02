package com.rewabank.customers.controller;

import com.rewabank.customers.dto.KycSubmitRequest;
import com.rewabank.customers.dto.KycVerifyRequest;
import com.rewabank.customers.entity.KycDocument;
import com.rewabank.customers.service.DocumentService;
import com.rewabank.customers.services.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC", description = "KYC submission and verification endpoints")
public class KycController {

    private final KycService kycService;
    private final DocumentService documentService;

    // Customer submits KYC details
    @PostMapping("/submit")
    @Operation(summary = "Submit KYC details")
    public ResponseEntity<Map<String, String>> submitKyc(
            @Valid @RequestBody KycSubmitRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        kycService.submitKyc(jwt.getSubject(), request);
        return ResponseEntity.ok(Map.of("message",
                "KYC submitted successfully. Our team will review within 2 business days."));
    }

    // Upload KYC document
    @PostMapping(value = "/documents/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload KYC document")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam KycDocument.DocumentType documentType,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        KycDocument doc = documentService.uploadDocument(
                jwt.getSubject(), documentType, file);
        return ResponseEntity.ok(Map.of(
                "documentId", doc.getId(),
                "documentType", doc.getDocumentType(),
                "status", doc.getStatus(),
                "message", "Document uploaded successfully"
        ));
    }

    // RM: start review
    @PatchMapping("/{customerId}/start-review")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Start KYC review — RM only")
    public ResponseEntity<Map<String, String>> startReview(@PathVariable UUID customerId) {
        kycService.startReview(customerId);
        return ResponseEntity.ok(Map.of("message", "KYC moved to UNDER_REVIEW"));
    }

    // RM: verify or reject
    @PatchMapping("/{customerId}/verify")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Verify or reject KYC — RM only")
    public ResponseEntity<Map<String, String>> verifyKyc(
            @PathVariable UUID customerId,
            @Valid @RequestBody KycVerifyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        kycService.verifyKyc(customerId, jwt.getSubject(), request);
        return ResponseEntity.ok(Map.of("message",
                "KYC " + request.decision() + " successfully"));
    }

    // Get document presigned URL — RM only
    @GetMapping("/documents/{documentId}/view-url")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','BRANCH_MANAGER','SUPER_ADMIN','AUDITOR')")
    @Operation(summary = "Get document view URL (15 min expiry)")
    public ResponseEntity<Map<String, String>> getDocumentUrl(
            @PathVariable UUID documentId) {
        String url = documentService.getDocumentViewUrl(documentId);
        return ResponseEntity.ok(Map.of("url", url, "expiresInMinutes", "15"));
    }
}
