package com.rewabank.audit.controller;

import com.rewabank.audit.entity.AuditLog;
import com.rewabank.audit.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Read-only endpoints.
 * SecurityConfig denies ALL POST/PUT/DELETE/PATCH.
 * Only GET allowed — AUDITOR + SUPER_ADMIN roles.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Read-only audit log endpoints")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping("/user/{keycloakUserId}")
    @PreAuthorize("hasAnyRole('AUDITOR','SUPER_ADMIN')")
    @Operation(summary = "Get audit log for a user")
    public ResponseEntity<Page<AuditLog>> getByUser(
            @PathVariable String keycloakUserId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(
                auditLogRepository
                        .findByKeycloakUserIdOrderByRecordedAtDesc(
                                keycloakUserId, pageable));
    }

    @GetMapping("/aggregate/{aggregateId}")
    @PreAuthorize("hasAnyRole('AUDITOR','SUPER_ADMIN')")
    @Operation(summary = "Get audit log for an entity (account/transaction/loan)")
    public ResponseEntity<Page<AuditLog>> getByAggregate(
            @PathVariable String aggregateId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(
                auditLogRepository
                        .findByAggregateIdOrderByRecordedAtDesc(
                                aggregateId, pageable));
    }

    @GetMapping("/event-type/{eventType}")
    @PreAuthorize("hasAnyRole('AUDITOR','SUPER_ADMIN')")
    @Operation(summary = "Get audit log by event type")
    public ResponseEntity<Page<AuditLog>> getByEventType(
            @PathVariable String eventType,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(
                auditLogRepository
                        .findByEventTypeOrderByRecordedAtDesc(
                                eventType, pageable));
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyRole('AUDITOR','SUPER_ADMIN')")
    @Operation(summary = "Get audit log by date range — RBI reporting")
    public ResponseEntity<Page<AuditLog>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @PageableDefault(size = 100) Pageable pageable) {
        return ResponseEntity.ok(
                auditLogRepository.findByDateRange(from, to, pageable));
    }
}
