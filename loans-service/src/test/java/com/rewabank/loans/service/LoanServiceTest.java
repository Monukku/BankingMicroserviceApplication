package com.rewabank.loans.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.loans.client.AccountsFeignClient;
import com.rewabank.loans.client.FraudFeignClient;
import com.rewabank.loans.dto.LoanApplicationRequest;
import com.rewabank.loans.dto.LoanApplicationResponse;
import com.rewabank.loans.dto.LoanReviewRequest;
import com.rewabank.loans.entity.LoanApplication;
import com.rewabank.loans.entity.OutboxEvent;
import com.rewabank.loans.exception.LoanException;
import com.rewabank.loans.repository.LoanApplicationRepository;
import com.rewabank.loans.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanServiceTest {

    @Mock LoanApplicationRepository loanRepository;
    @Mock OutboxEventRepository      outboxRepository;
    @Mock AccountsFeignClient        accountsClient;
    @Mock FraudFeignClient           fraudClient;
    @Mock ObjectMapper               objectMapper;

    @InjectMocks LoanService loanService;

    private UUID loanId;
    private UUID accountId;
    private LoanApplication appliedLoan;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        loanId    = UUID.randomUUID();
        accountId = UUID.randomUUID();

        appliedLoan = LoanApplication.builder()
                .id(loanId)
                .keycloakUserId("kc-user-1")
                .accountId(accountId)
                .loanType(LoanApplication.LoanType.PERSONAL)
                .requestedAmount(new BigDecimal("500000.00"))
                .tenureMonths(24)
                .purpose("Home renovation")
                .status(LoanApplication.LoanStatus.APPLIED)
                .appliedAt(LocalDateTime.now())
                .build();

        // allow outbox serialization in all tests
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // default fraud response — ALLOW with score 0
        when(fraudClient.getScore(any(), any(), any(), any()))
                .thenReturn(Map.of("score", 0, "action", "ALLOW", "reason", "clean"));
    }

    // ── apply ────────────────────────────────────────────────────

    @Test
    void apply_createsLoanWithAppliedStatus() {
        when(loanRepository.save(any(LoanApplication.class)))
                .thenAnswer(inv -> {
                    LoanApplication l = inv.getArgument(0);
                    if (l.getId() == null) l.setId(UUID.randomUUID());
                    return l;
                });

        LoanApplicationRequest req = new LoanApplicationRequest(
                accountId, LoanApplication.LoanType.PERSONAL,
                new BigDecimal("500000"), 24, "Home renovation");

        LoanApplicationResponse response = loanService.apply("kc-user-1", req);

        assertThat(response.status()).isEqualTo(LoanApplication.LoanStatus.APPLIED);
        assertThat(response.keycloakUserId()).isEqualTo("kc-user-1");
        assertThat(response.requestedAmount()).isEqualByComparingTo("500000");
        verify(loanRepository).save(any(LoanApplication.class));
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    // ── startReview ──────────────────────────────────────────────

    @Test
    void startReview_applied_movesToUnderReview() {
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanApplicationResponse response = loanService.startReview(loanId, "rm-001");

        assertThat(response.status()).isEqualTo(LoanApplication.LoanStatus.UNDER_REVIEW);
        assertThat(appliedLoan.getReviewerId()).isEqualTo("rm-001");
        assertThat(appliedLoan.getReviewedAt()).isNotNull();
        verify(loanRepository).save(appliedLoan);
    }

    @Test
    void startReview_alreadyUnderReview_throws() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.UNDER_REVIEW);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));

        assertThatThrownBy(() -> loanService.startReview(loanId, "rm-001"))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("cannot be reviewed");
    }

    @Test
    void startReview_notFound_throws() {
        when(loanRepository.findById(loanId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.startReview(loanId, "rm-001"))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("not found");
    }

    // ── review — APPROVE ─────────────────────────────────────────

    @Test
    void review_approve_withinCreditOfficerLimit_setsApproved() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.UNDER_REVIEW);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanApplicationResponse response = loanService.review(
                loanId, "officer-1", "CREDIT_OFFICER",
                new LoanReviewRequest("APPROVE", new BigDecimal("500000"),
                        new BigDecimal("12.5"), "Good profile", null));

        assertThat(response.status()).isEqualTo(LoanApplication.LoanStatus.APPROVED);
        assertThat(response.approvedAmount()).isEqualByComparingTo("500000");
        assertThat(appliedLoan.getApprovedAt()).isNotNull();
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void review_approve_exceedsCreditOfficerLimit_throws() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.UNDER_REVIEW);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));

        // CREDIT_OFFICER limit is 10L; requesting 15L
        assertThatThrownBy(() -> loanService.review(
                loanId, "officer-1", "CREDIT_OFFICER",
                new LoanReviewRequest("APPROVE", new BigDecimal("1500000"),
                        null, null, null)))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("exceeds your approval limit");
    }

    @Test
    void review_approve_branchManagerCanApproveHigherAmount() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.UNDER_REVIEW);
        appliedLoan.setRequestedAmount(new BigDecimal("3000000"));
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanApplicationResponse response = loanService.review(
                loanId, "mgr-1", "BRANCH_MANAGER",
                new LoanReviewRequest("APPROVE", new BigDecimal("3000000"),
                        null, null, null));

        assertThat(response.status()).isEqualTo(LoanApplication.LoanStatus.APPROVED);
    }

    @Test
    void review_approve_nullApprovedAmount_usesRequestedAmount() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.UNDER_REVIEW);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        loanService.review(loanId, "officer-1", "CREDIT_OFFICER",
                new LoanReviewRequest("APPROVE", null, null, null, null));

        assertThat(appliedLoan.getApprovedAmount())
                .isEqualByComparingTo(appliedLoan.getRequestedAmount());
    }

    @Test
    void review_approve_nullInterestRate_usesDefault() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.UNDER_REVIEW);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        loanService.review(loanId, "officer-1", "CREDIT_OFFICER",
                new LoanReviewRequest("APPROVE", new BigDecimal("400000"),
                        null, null, null));

        // PERSONAL default rate is 12.00
        assertThat(appliedLoan.getInterestRate())
                .isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    void review_approve_fromAppliedStatus_allowed() {
        // canApprove() is true for both APPLIED and UNDER_REVIEW
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanApplicationResponse response = loanService.review(
                loanId, "officer-1", "CREDIT_OFFICER",
                new LoanReviewRequest("APPROVE", new BigDecimal("500000"),
                        null, null, null));

        assertThat(response.status()).isEqualTo(LoanApplication.LoanStatus.APPROVED);
    }

    // ── review — REJECT ──────────────────────────────────────────

    @Test
    void review_reject_setsRejectedAndSavesOutbox() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.UNDER_REVIEW);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanApplicationResponse response = loanService.review(
                loanId, "officer-1", "CREDIT_OFFICER",
                new LoanReviewRequest("REJECT", null, null, null,
                        "Low credit score"));

        assertThat(response.status()).isEqualTo(LoanApplication.LoanStatus.REJECTED);
        assertThat(appliedLoan.getRejectionReason()).isEqualTo("Low credit score");
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void review_reject_missingReason_throws() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.UNDER_REVIEW);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));

        assertThatThrownBy(() -> loanService.review(
                loanId, "officer-1", "CREDIT_OFFICER",
                new LoanReviewRequest("REJECT", null, null, null, null)))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("Rejection reason is required");
    }

    @Test
    void review_reject_blankReason_throws() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.UNDER_REVIEW);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));

        assertThatThrownBy(() -> loanService.review(
                loanId, "officer-1", "CREDIT_OFFICER",
                new LoanReviewRequest("REJECT", null, null, null, "  ")))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("Rejection reason is required");
    }

    @Test
    void review_invalidDecision_throws() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.UNDER_REVIEW);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));

        assertThatThrownBy(() -> loanService.review(
                loanId, "officer-1", "CREDIT_OFFICER",
                new LoanReviewRequest("MAYBE", null, null, null, null)))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("Invalid decision");
    }

    @Test
    void review_disbursedLoan_cannotBeReviewed_throws() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.DISBURSED);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));

        assertThatThrownBy(() -> loanService.review(
                loanId, "officer-1", "CREDIT_OFFICER",
                new LoanReviewRequest("APPROVE", null, null, null, null)))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("cannot be reviewed");
    }

    // ── disburse ─────────────────────────────────────────────────

    @Test
    void disburse_approved_creditAccountAndSetsDisbursed() {
        appliedLoan.setStatus(LoanApplication.LoanStatus.APPROVED);
        appliedLoan.setApprovedAmount(new BigDecimal("500000.00"));
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanApplicationResponse response = loanService.disburse(loanId, "officer-1");

        assertThat(response.status()).isEqualTo(LoanApplication.LoanStatus.DISBURSED);
        assertThat(appliedLoan.getDisbursedAt()).isNotNull();
        verify(accountsClient).creditAccount(eq(accountId), any());
        verify(outboxRepository, atLeastOnce()).save(any(OutboxEvent.class));
    }

    @Test
    void disburse_notApproved_throws() {
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));

        assertThatThrownBy(() -> loanService.disburse(loanId, "officer-1"))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("cannot be disbursed");
    }

    @Test
    void disburse_notFound_throws() {
        when(loanRepository.findById(loanId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.disburse(loanId, "officer-1"))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("not found");
    }

    // ── queries ──────────────────────────────────────────────────

    @Test
    void getById_found_returnsResponse() {
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(appliedLoan));

        LoanApplicationResponse response = loanService.getById(loanId);

        assertThat(response.id()).isEqualTo(loanId);
        assertThat(response.loanType()).isEqualTo(LoanApplication.LoanType.PERSONAL);
    }

    @Test
    void getById_notFound_throws() {
        UUID unknown = UUID.randomUUID();
        when(loanRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.getById(unknown))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getMyLoans_returnsAllForUser() {
        LoanApplication second = LoanApplication.builder()
                .id(UUID.randomUUID()).keycloakUserId("kc-user-1")
                .accountId(accountId).loanType(LoanApplication.LoanType.HOME)
                .requestedAmount(new BigDecimal("2000000")).tenureMonths(120)
                .purpose("Buy home").status(LoanApplication.LoanStatus.APPLIED)
                .build();
        when(loanRepository.findByKeycloakUserIdOrderByCreatedAtDesc("kc-user-1"))
                .thenReturn(List.of(appliedLoan, second));

        List<LoanApplicationResponse> loans = loanService.getMyLoans("kc-user-1");

        assertThat(loans).hasSize(2);
    }

    @Test
    void getMyLoans_noLoans_returnsEmptyList() {
        when(loanRepository.findByKeycloakUserIdOrderByCreatedAtDesc("kc-user-1"))
                .thenReturn(List.of());

        assertThat(loanService.getMyLoans("kc-user-1")).isEmpty();
    }

    // ── fraud check on apply ─────────────────────────────────────

    @Test
    void apply_fraudBlock_autoRejectsWithException() {
        when(fraudClient.getScore(any(), any(), any(), any()))
                .thenReturn(Map.of("score", 95, "action", "BLOCK", "reason", "velocity"));

        LoanApplicationRequest req = new LoanApplicationRequest(
                accountId, LoanApplication.LoanType.PERSONAL,
                new BigDecimal("500000"), 24, "Home renovation");

        assertThatThrownBy(() -> loanService.apply("kc-user-1", req))
                .isInstanceOf(LoanException.class)
                .hasMessageContaining("fraud risk");

        verify(loanRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void apply_fraudReview_storesFraudScoreAndContinues() {
        when(fraudClient.getScore(any(), any(), any(), any()))
                .thenReturn(Map.of("score", 65, "action", "REVIEW", "reason", "large amount"));
        when(loanRepository.save(any(LoanApplication.class)))
                .thenAnswer(inv -> {
                    LoanApplication l = inv.getArgument(0);
                    if (l.getId() == null) l.setId(UUID.randomUUID());
                    return l;
                });

        LoanApplicationRequest req = new LoanApplicationRequest(
                accountId, LoanApplication.LoanType.PERSONAL,
                new BigDecimal("500000"), 24, "Home renovation");

        LoanApplicationResponse response = loanService.apply("kc-user-1", req);

        assertThat(response.status()).isEqualTo(LoanApplication.LoanStatus.APPLIED);
        assertThat(response.fraudScore()).isEqualTo(65);
        assertThat(response.fraudAction()).isEqualTo("REVIEW");
        verify(loanRepository).save(any(LoanApplication.class));
    }

    @Test
    void apply_fraudAllow_storesFraudScore() {
        when(loanRepository.save(any(LoanApplication.class)))
                .thenAnswer(inv -> {
                    LoanApplication l = inv.getArgument(0);
                    if (l.getId() == null) l.setId(UUID.randomUUID());
                    return l;
                });

        LoanApplicationRequest req = new LoanApplicationRequest(
                accountId, LoanApplication.LoanType.HOME,
                new BigDecimal("2000000"), 120, "Buy house");

        LoanApplicationResponse response = loanService.apply("kc-user-1", req);

        assertThat(response.fraudScore()).isEqualTo(0);
        assertThat(response.fraudAction()).isEqualTo("ALLOW");
    }
}
