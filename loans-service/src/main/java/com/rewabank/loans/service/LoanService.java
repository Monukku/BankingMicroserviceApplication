package com.rewabank.loans.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.loans.client.AccountsFeignClient;
import com.rewabank.loans.client.FraudFeignClient;
import com.rewabank.loans.dto.*;
import com.rewabank.loans.entity.LoanApplication;
import com.rewabank.loans.entity.OutboxEvent;
import com.rewabank.loans.exception.LoanException;
import com.rewabank.loans.repository.LoanApplicationRepository;
import com.rewabank.loans.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

    private final LoanApplicationRepository loanRepository;
    private final OutboxEventRepository     outboxRepository;
    private final AccountsFeignClient       accountsClient;
    private final FraudFeignClient          fraudClient;
    private final ObjectMapper              objectMapper;

    // Role-based approval limits
    private static final BigDecimal CREDIT_OFFICER_LIMIT =
            new BigDecimal("1000000.00");   // 10L
    private static final BigDecimal BRANCH_MANAGER_LIMIT =
            new BigDecimal("5000000.00");   // 50L

    private static final String TOPIC_APPLIED   = "bank.loan.applied";
    private static final String TOPIC_APPROVED  = "bank.loan.approved";
    private static final String TOPIC_REJECTED  = "bank.loan.rejected";
    private static final String TOPIC_DISBURSED = "bank.loan.disbursed";

    // ── Apply for loan ────────────────────────────────────────────────────────
    @Transactional
    public LoanApplicationResponse apply(String keycloakUserId,
                                         LoanApplicationRequest request) {
        // Fraud check at application time — auto-reject on BLOCK, store score for reviewers
        Map<String, Object> fraudResult = fraudClient.getScore(
                request.accountId(),
                request.requestedAmount(),
                "LOAN_APPLICATION",
                UUID.randomUUID().toString()
        );

        int fraudScore  = ((Number) fraudResult.getOrDefault("score", 0)).intValue();
        String fraudAction = String.valueOf(fraudResult.getOrDefault("action", "ALLOW"));

        if ("BLOCK".equalsIgnoreCase(fraudAction)) {
            log.warn("Loan application auto-rejected — fraud BLOCK for accountId: {} score: {}",
                    request.accountId(), fraudScore);
            throw new LoanException("LOAN_006",
                    "Loan application declined due to fraud risk. Please contact your branch.");
        }

        LoanApplication loan = LoanApplication.builder()
                .keycloakUserId(keycloakUserId)
                .accountId(request.accountId())
                .loanType(request.loanType())
                .requestedAmount(request.requestedAmount())
                .tenureMonths(request.tenureMonths())
                .purpose(request.purpose())
                .status(LoanApplication.LoanStatus.APPLIED)
                .fraudScore(fraudScore)
                .fraudAction(fraudAction)
                .appliedAt(LocalDateTime.now())
                .build();

        loanRepository.save(loan);

        saveOutbox(loan.getId().toString(), "LOAN_APPLIED",
                TOPIC_APPLIED, buildPayload(loan, "LOAN_APPLIED"));

        log.info("Loan application created: {} amount: {}",
                loan.getId(), request.requestedAmount());

        return toResponse(loan);
    }

    // ── Review loan (start review) ────────────────────────────────────────────
    @Transactional
    @PreAuthorize("hasAnyRole('CREDIT_OFFICER','BRANCH_MANAGER','SUPER_ADMIN')")
    public LoanApplicationResponse startReview(UUID loanId,
                                               String reviewerId) {
        LoanApplication loan = findLoan(loanId);

        if (!loan.canReview()) {
            throw new LoanException("LOAN_002",
                    "Loan cannot be reviewed from status: " + loan.getStatus());
        }

        loan.setStatus(LoanApplication.LoanStatus.UNDER_REVIEW);
        loan.setReviewerId(reviewerId);
        loan.setReviewedAt(LocalDateTime.now());
        loanRepository.save(loan);

        log.info("Loan {} moved to UNDER_REVIEW by {}", loanId, reviewerId);
        return toResponse(loan);
    }

    // ── Approve or Reject loan ────────────────────────────────────────────────
    @Transactional
    @PreAuthorize("hasAnyRole('CREDIT_OFFICER','BRANCH_MANAGER','SUPER_ADMIN')")
    public LoanApplicationResponse review(UUID loanId, String reviewerId,
                                          String reviewerRole,
                                          LoanReviewRequest request) {
        LoanApplication loan = findLoan(loanId);

        if (!loan.canApprove()) {
            throw new LoanException("LOAN_002",
                    "Loan cannot be reviewed from status: " + loan.getStatus());
        }

        if ("APPROVE".equalsIgnoreCase(request.decision())) {
            // Validate approved amount is within role limit
            BigDecimal limit = approvalLimit(reviewerRole);
            BigDecimal approvedAmt = request.approvedAmount() != null
                    ? request.approvedAmount()
                    : loan.getRequestedAmount();

            if (approvedAmt.compareTo(limit) > 0) {
                throw new LoanException("LOAN_003",
                        "Approved amount Rs" + approvedAmt
                                + " exceeds your approval limit of Rs" + limit
                                + " for role " + reviewerRole);
            }

            loan.setStatus(LoanApplication.LoanStatus.APPROVED);
            loan.setApprovedAmount(approvedAmt);
            loan.setInterestRate(request.interestRate() != null
                    ? request.interestRate()
                    : defaultInterestRate(loan.getLoanType()));
            loan.setReviewerId(reviewerId);
            loan.setReviewerRole(reviewerRole);
            loan.setReviewNotes(request.reviewNotes());
            loan.setApprovedAt(LocalDateTime.now());
            loanRepository.save(loan);

            saveOutbox(loan.getId().toString(), "LOAN_APPROVED",
                    TOPIC_APPROVED, buildPayload(loan, "LOAN_APPROVED"));

            log.info("Loan {} APPROVED by {} amount: {}",
                    loanId, reviewerId, approvedAmt);

        } else if ("REJECT".equalsIgnoreCase(request.decision())) {
            if (request.rejectionReason() == null
                    || request.rejectionReason().isBlank()) {
                throw new LoanException("LOAN_004",
                        "Rejection reason is required");
            }

            loan.setStatus(LoanApplication.LoanStatus.REJECTED);
            loan.setReviewerId(reviewerId);
            loan.setReviewerRole(reviewerRole);
            loan.setRejectionReason(request.rejectionReason());
            loan.setReviewNotes(request.reviewNotes());
            loanRepository.save(loan);

            saveOutbox(loan.getId().toString(), "LOAN_REJECTED",
                    TOPIC_REJECTED, buildPayload(loan, "LOAN_REJECTED"));

            log.info("Loan {} REJECTED by {} reason: {}",
                    loanId, reviewerId, request.rejectionReason());

        } else {
            throw new LoanException("LOAN_005",
                    "Invalid decision. Use APPROVE or REJECT");
        }

        return toResponse(loan);
    }

    // ── Disburse loan ─────────────────────────────────────────────────────────
    @Transactional
    @PreAuthorize("hasAnyRole('CREDIT_OFFICER','BRANCH_MANAGER','SUPER_ADMIN')")
    public LoanApplicationResponse disburse(UUID loanId,
                                            String disbursedBy) {
        LoanApplication loan = findLoan(loanId);

        if (!loan.canDisburse()) {
            throw new LoanException("LOAN_002",
                    "Loan cannot be disbursed from status: " + loan.getStatus());
        }

        // Sync call to Accounts MS — credit the account
        accountsClient.creditAccount(
                loan.getAccountId(),
                new AccountCreditRequest(
                        loan.getAccountId(),
                        loan.getApprovedAmount(),
                        loan.getId().toString()
                )
        );

        loan.setStatus(LoanApplication.LoanStatus.DISBURSED);
        loan.setDisbursedAt(LocalDateTime.now());
        loanRepository.save(loan);

        saveOutbox(loan.getId().toString(), "LOAN_DISBURSED",
                TOPIC_DISBURSED, buildPayload(loan, "LOAN_DISBURSED"));

        log.info("Loan {} DISBURSED amount: {} to account: {}",
                loanId, loan.getApprovedAmount(), loan.getAccountId());

        return toResponse(loan);
    }

    // ── Queries ───────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<LoanApplicationResponse> getMyLoans(String keycloakUserId) {
        return loanRepository
                .findByKeycloakUserIdOrderByCreatedAtDesc(keycloakUserId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LoanApplicationResponse getById(UUID id) {
        return toResponse(findLoan(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('CREDIT_OFFICER','BRANCH_MANAGER'," +
            "'AUDITOR','SUPER_ADMIN')")
    public Page<LoanApplicationResponse> getAllLoans(Pageable pageable) {
        return loanRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('CREDIT_OFFICER','BRANCH_MANAGER','SUPER_ADMIN')")
    public Page<LoanApplicationResponse> getByStatus(
            LoanApplication.LoanStatus status, Pageable pageable) {
        return loanRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(this::toResponse);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private LoanApplication findLoan(UUID id) {
        return loanRepository.findById(id)
                .orElseThrow(() -> new LoanException("LOAN_001",
                        "Loan application not found: " + id));
    }

    private BigDecimal approvalLimit(String role) {
        if (role == null) return BigDecimal.ZERO;
        return switch (role.toUpperCase()) {
            case "BRANCH_MANAGER", "SUPER_ADMIN" -> BRANCH_MANAGER_LIMIT;
            case "CREDIT_OFFICER"                -> CREDIT_OFFICER_LIMIT;
            default                              -> BigDecimal.ZERO;
        };
    }

    private BigDecimal defaultInterestRate(LoanApplication.LoanType type) {
        return switch (type) {
            case HOME      -> new BigDecimal("8.50");
            case EDUCATION -> new BigDecimal("9.00");
            case VEHICLE   -> new BigDecimal("9.50");
            case PERSONAL  -> new BigDecimal("12.00");
            case BUSINESS  -> new BigDecimal("11.00");
            case GOLD      -> new BigDecimal("7.50");
        };
    }

    private void saveOutbox(String aggregateId, String eventType,
                            String topic, Map<String, Object> payload) {
        try {
            outboxRepository.save(OutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Outbox serialization failed: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(LoanApplication loan,
                                             String eventType) {
        return Map.of(
                "eventType",     eventType,
                "loanId",        loan.getId().toString(),
                "keycloakUserId",loan.getKeycloakUserId(),
                "accountId",     loan.getAccountId().toString(),
                "loanType",      loan.getLoanType().name(),
                "amount",        loan.getApprovedAmount() != null
                        ? loan.getApprovedAmount().toString()
                        : loan.getRequestedAmount().toString(),
                "status",        loan.getStatus().name(),
                "occurredAt",    LocalDateTime.now().toString()
        );
    }

    private LoanApplicationResponse toResponse(LoanApplication l) {
        return new LoanApplicationResponse(
                l.getId(), l.getKeycloakUserId(), l.getAccountId(),
                l.getLoanType(), l.getRequestedAmount(), l.getApprovedAmount(),
                l.getInterestRate(), l.getTenureMonths(), l.getStatus(),
                l.getPurpose(), l.getRejectionReason(), l.getReviewNotes(),
                l.getFraudScore(), l.getFraudAction(),
                l.getAppliedAt(), l.getApprovedAt(), l.getDisbursedAt(),
                l.getCreatedAt()
        );
    }
}