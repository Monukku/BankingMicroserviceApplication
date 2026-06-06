package com.rewabank.fraud.service;

import com.rewabank.fraud.kafka.FraudEventProducer;
import com.rewabank.fraud.model.FraudAlert;
import com.rewabank.fraud.repository.FraudAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudAlertServiceTest {

    @Mock
    private FraudAlertRepository fraudAlertRepository;

    @Mock
    private FraudEventProducer fraudEventProducer;

    @InjectMocks
    private FraudAlertService fraudAlertService;

    private UUID accountId;
    private UUID alertId;
    private FraudAlert openAlert;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        alertId   = UUID.randomUUID();

        openAlert = FraudAlert.builder()
                .id(alertId)
                .accountId(accountId)
                .keycloakUserId("user-001")
                .transactionId("txn-001")
                .amount(new BigDecimal("75000.00"))
                .fraudScore(65)
                .action("FLAG")
                .triggeredRules("LARGE_AMOUNT(+30), HIGH_VELOCITY(+40)")
                .status(FraudAlert.AlertStatus.OPEN)
                .build();
    }

    // ── createAlert ───────────────────────────────────────────────────────────

    // Simulates JPA behaviour: save() sets the generated ID on the passed entity
    private Answer<FraudAlert> setIdOnSave() {
        return invocation -> {
            FraudAlert a = invocation.getArgument(0);
            a.setId(alertId);
            return a;
        };
    }

    @Test
    void createAlert_ShouldSaveAlertWithOpenStatus() {
        when(fraudAlertRepository.save(any(FraudAlert.class))).thenAnswer(setIdOnSave());

        fraudAlertService.createAlert(
                accountId, "user-001", "txn-001",
                new BigDecimal("75000.00"), 65, "FLAG",
                "LARGE_AMOUNT(+30), HIGH_VELOCITY(+40)");

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudAlertRepository).save(captor.capture());
        assertEquals(FraudAlert.AlertStatus.OPEN, captor.getValue().getStatus());
        assertEquals(accountId, captor.getValue().getAccountId());
        assertEquals(65, captor.getValue().getFraudScore());
        assertEquals("FLAG", captor.getValue().getAction());
    }

    @Test
    void createAlert_ShouldPublishAlertRaisedEvent() {
        when(fraudAlertRepository.save(any(FraudAlert.class))).thenAnswer(setIdOnSave());

        fraudAlertService.createAlert(
                accountId, "user-001", "txn-001",
                new BigDecimal("75000.00"), 65, "FLAG", "LARGE_AMOUNT(+30)");

        verify(fraudEventProducer).publishAlertRaised(
                eq(alertId.toString()),
                eq(accountId.toString()),
                eq("user-001"),
                eq("txn-001"),
                eq("75000.00"),
                eq(65),
                eq("FLAG"),
                eq("LARGE_AMOUNT(+30)"));
    }

    @Test
    void createAlert_ShouldReturnSavedAlertWithGeneratedId() {
        when(fraudAlertRepository.save(any(FraudAlert.class))).thenAnswer(setIdOnSave());

        FraudAlert result = fraudAlertService.createAlert(
                accountId, "user-001", "txn-001",
                new BigDecimal("75000.00"), 65, "FLAG", "LARGE_AMOUNT(+30)");

        assertNotNull(result);
        assertEquals(alertId, result.getId());
    }

    // ── resolveAlert ──────────────────────────────────────────────────────────

    @Test
    void resolveAlert_ShouldSetStatusToResolved_WhenResolutionIsNotFalsePositive() {
        when(fraudAlertRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
        when(fraudAlertRepository.save(any(FraudAlert.class))).thenReturn(openAlert);

        FraudAlert resolved = fraudAlertService.resolveAlert(
                alertId, "reviewer-001", "Verified legitimate transaction", "RESOLVED");

        assertEquals(FraudAlert.AlertStatus.RESOLVED, resolved.getStatus());
        assertEquals("reviewer-001", resolved.getResolvedBy());
        assertEquals("Verified legitimate transaction", resolved.getResolutionNotes());
        assertNotNull(resolved.getResolvedAt());
    }

    @Test
    void resolveAlert_ShouldSetStatusToFalsePositive_WhenResolutionIsFalsePositive() {
        when(fraudAlertRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
        when(fraudAlertRepository.save(any(FraudAlert.class))).thenReturn(openAlert);

        FraudAlert resolved = fraudAlertService.resolveAlert(
                alertId, "reviewer-002", "Customer confirmed this was them", "FALSE_POSITIVE");

        assertEquals(FraudAlert.AlertStatus.FALSE_POSITIVE, resolved.getStatus());
    }

    @Test
    void resolveAlert_ShouldPublishAlertClearedEvent() {
        when(fraudAlertRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
        when(fraudAlertRepository.save(any(FraudAlert.class))).thenReturn(openAlert);

        fraudAlertService.resolveAlert(alertId, "reviewer-001", "Legitimate", "RESOLVED");

        verify(fraudEventProducer).publishAlertCleared(
                eq(alertId.toString()),
                eq(accountId.toString()),
                eq("RESOLVED"));
    }

    @Test
    void resolveAlert_ShouldThrowException_WhenAlertNotFound() {
        when(fraudAlertRepository.findById(alertId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> fraudAlertService.resolveAlert(
                        alertId, "reviewer", "notes", "RESOLVED"));
    }

    // ── getOpenAlerts ─────────────────────────────────────────────────────────

    @Test
    void getOpenAlerts_ShouldReturnPagedOpenAlerts() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<FraudAlert> expectedPage = new PageImpl<>(List.of(openAlert));
        when(fraudAlertRepository.findByStatusOrderByCreatedAtDesc(
                FraudAlert.AlertStatus.OPEN, pageable)).thenReturn(expectedPage);

        Page<FraudAlert> result = fraudAlertService.getOpenAlerts(pageable);

        assertEquals(1, result.getTotalElements());
        assertFalse(result.getContent().isEmpty(), "Result page must contain at least one element");
        assertEquals(FraudAlert.AlertStatus.OPEN, result.getContent().get(0).getStatus());
    }

    @Test
    void getOpenAlerts_ShouldReturnEmptyPage_WhenNoOpenAlerts() {
        Pageable pageable = PageRequest.of(0, 10);
        when(fraudAlertRepository.findByStatusOrderByCreatedAtDesc(
                FraudAlert.AlertStatus.OPEN, pageable))
                .thenReturn(Page.empty());

        Page<FraudAlert> result = fraudAlertService.getOpenAlerts(pageable);

        assertTrue(result.isEmpty());
    }

    // ── getByAccount ──────────────────────────────────────────────────────────

    @Test
    void getByAccount_ShouldReturnAlertsForSpecificAccount() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<FraudAlert> expectedPage = new PageImpl<>(List.of(openAlert));
        when(fraudAlertRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable))
                .thenReturn(expectedPage);

        Page<FraudAlert> result = fraudAlertService.getByAccount(accountId, pageable);

        assertEquals(1, result.getTotalElements());
        assertFalse(result.getContent().isEmpty(), "Result page must contain at least one element");
        assertEquals(accountId, result.getContent().get(0).getAccountId());
    }

    @Test
    void getByAccount_ShouldReturnEmptyPage_WhenAccountHasNoAlerts() {
        Pageable pageable = PageRequest.of(0, 10);
        when(fraudAlertRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable))
                .thenReturn(Page.empty());

        Page<FraudAlert> result = fraudAlertService.getByAccount(accountId, pageable);

        assertTrue(result.isEmpty());
    }
}