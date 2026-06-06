package com.rewabank.loans.service;

import com.rewabank.loans.entity.EmiSchedule;
import com.rewabank.loans.entity.Loan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EmiCalculatorServiceTest {

    private final EmiCalculatorService emiService = new EmiCalculatorService();

    private static final UUID LOAN_ID = UUID.randomUUID();

    // ── calculateEmi ─────────────────────────────────────────────

    @Test
    void calculateEmi_zeroRate_equalSplit() {
        BigDecimal emi = emiService.calculateEmi(
                new BigDecimal("120000"), BigDecimal.ZERO, 12);

        assertThat(emi).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    void calculateEmi_standardRate_matchesFormula() {
        // 1,00,000 at 12% p.a. for 12 months → known EMI ≈ 8884.87
        BigDecimal emi = emiService.calculateEmi(
                new BigDecimal("100000"), new BigDecimal("12"), 12);

        assertThat(emi).isBetween(new BigDecimal("8880"), new BigDecimal("8890"));
    }

    @Test
    void calculateEmi_higherRate_givesHigherEmi() {
        BigDecimal emiLow  = emiService.calculateEmi(
                new BigDecimal("100000"), new BigDecimal("8"), 24);
        BigDecimal emiHigh = emiService.calculateEmi(
                new BigDecimal("100000"), new BigDecimal("16"), 24);

        assertThat(emiHigh).isGreaterThan(emiLow);
    }

    @Test
    void calculateEmi_longerTenure_givesLowerEmi() {
        BigDecimal emiShort = emiService.calculateEmi(
                new BigDecimal("500000"), new BigDecimal("10"), 12);
        BigDecimal emiLong  = emiService.calculateEmi(
                new BigDecimal("500000"), new BigDecimal("10"), 60);

        assertThat(emiLong).isLessThan(emiShort);
    }

    @Test
    void calculateEmi_resultHasTwoDecimalPlaces() {
        BigDecimal emi = emiService.calculateEmi(
                new BigDecimal("350000"), new BigDecimal("9.5"), 36);

        assertThat(emi.scale()).isEqualTo(2);
    }

    // ── generateEmiSchedule ──────────────────────────────────────

    @Test
    void generateEmiSchedule_sizeEqualsTenure() {
        List<EmiSchedule> schedule = emiService.generateEmiSchedule(
                LOAN_ID, new BigDecimal("120000"), new BigDecimal("12"), 12,
                LocalDate.of(2025, 1, 1));

        assertThat(schedule).hasSize(12);
    }

    @Test
    void generateEmiSchedule_allStatusesPending() {
        List<EmiSchedule> schedule = emiService.generateEmiSchedule(
                LOAN_ID, new BigDecimal("100000"), new BigDecimal("10"), 6,
                LocalDate.of(2025, 1, 1));

        assertThat(schedule).allMatch(e -> e.getStatus() == EmiSchedule.EmiStatus.PENDING);
    }

    @Test
    void generateEmiSchedule_installmentNumbersSequential() {
        List<EmiSchedule> schedule = emiService.generateEmiSchedule(
                LOAN_ID, new BigDecimal("100000"), new BigDecimal("10"), 6,
                LocalDate.of(2025, 1, 1));

        for (int i = 0; i < schedule.size(); i++) {
            assertThat(schedule.get(i).getInstallmentNumber()).isEqualTo(i + 1);
        }
    }

    @Test
    void generateEmiSchedule_dueDatesAdvanceMonthly() {
        LocalDate first = LocalDate.of(2025, 3, 15);
        List<EmiSchedule> schedule = emiService.generateEmiSchedule(
                LOAN_ID, new BigDecimal("100000"), new BigDecimal("10"), 4, first);

        assertThat(schedule).isNotEmpty();
        assertThat(schedule.get(0).getDueDate()).isEqualTo(first);
        assertThat(schedule.get(1).getDueDate()).isEqualTo(first.plusMonths(1));
        assertThat(schedule.get(2).getDueDate()).isEqualTo(first.plusMonths(2));
        assertThat(schedule.get(3).getDueDate()).isEqualTo(first.plusMonths(3));
    }

    @Test
    void generateEmiSchedule_lastInstallmentOutstandingIsZero() {
        List<EmiSchedule> schedule = emiService.generateEmiSchedule(
                LOAN_ID, new BigDecimal("200000"), new BigDecimal("9"), 24,
                LocalDate.of(2025, 1, 1));

        BigDecimal lastOutstanding = schedule.get(schedule.size() - 1).getOutstandingAfter();
        assertThat(lastOutstanding).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void generateEmiSchedule_sumOfPrincipalPartsEqualsPrincipal() {
        BigDecimal principal = new BigDecimal("100000");
        List<EmiSchedule> schedule = emiService.generateEmiSchedule(
                LOAN_ID, principal, new BigDecimal("12"), 12,
                LocalDate.of(2025, 1, 1));

        BigDecimal totalPrincipal = schedule.stream()
                .map(EmiSchedule::getPrincipalPart)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Allow up to Rs 1 rounding tolerance across 12 installments
        assertThat(totalPrincipal)
                .isBetween(principal.subtract(BigDecimal.ONE),
                           principal.add(BigDecimal.ONE));
    }

    @Test
    void generateEmiSchedule_loanIdSetOnAllEntries() {
        List<EmiSchedule> schedule = emiService.generateEmiSchedule(
                LOAN_ID, new BigDecimal("50000"), new BigDecimal("8"), 6,
                LocalDate.of(2025, 1, 1));

        assertThat(schedule).allMatch(e -> LOAN_ID.equals(e.getLoanId()));
    }

    @Test
    void generateEmiSchedule_zeroRate_equalEmiEachMonth() {
        List<EmiSchedule> schedule = emiService.generateEmiSchedule(
                LOAN_ID, new BigDecimal("60000"), BigDecimal.ZERO, 6,
                LocalDate.of(2025, 1, 1));

        // All non-last EMIs should be equal for zero rate
        assertThat(schedule).isNotEmpty();
        BigDecimal firstEmi = schedule.get(0).getEmiAmount();
        schedule.subList(0, schedule.size() - 1)
                .forEach(e -> assertThat(e.getEmiAmount())
                        .isEqualByComparingTo(firstEmi));
    }

    // ── recalculateRemainingSchedule ─────────────────────────────

    @Test
    void recalculateRemainingSchedule_sizeEqualsRemainingMonths() {
        List<EmiSchedule> schedule = emiService.recalculateRemainingSchedule(
                LOAN_ID, new BigDecimal("80000"), new BigDecimal("11"),
                8, 5, LocalDate.of(2025, 6, 1));

        assertThat(schedule).hasSize(8);
    }

    @Test
    void recalculateRemainingSchedule_startInstallmentNumberApplied() {
        List<EmiSchedule> schedule = emiService.recalculateRemainingSchedule(
                LOAN_ID, new BigDecimal("80000"), new BigDecimal("11"),
                6, 7, LocalDate.of(2025, 8, 1));

        assertThat(schedule).isNotEmpty();
        assertThat(schedule.get(0).getInstallmentNumber()).isEqualTo(7);
        assertThat(schedule.get(5).getInstallmentNumber()).isEqualTo(12);
    }

    // ── calculateBulletRepaymentAmount ───────────────────────────

    @Test
    void calculateBulletRepayment_correctSimpleInterest() {
        // P=100000, rate=12%, 12 months → interest = 100000 * 0.12 * 1 = 12000
        BigDecimal total = emiService.calculateBulletRepaymentAmount(
                new BigDecimal("100000"), new BigDecimal("12"), 12);

        assertThat(total).isEqualByComparingTo(new BigDecimal("112000.00"));
    }

    @Test
    void calculateBulletRepayment_sixMonthTenure_halfYearInterest() {
        // P=100000, rate=10%, 6 months → interest = 100000 * 0.10 * 0.5 = 5000
        BigDecimal total = emiService.calculateBulletRepaymentAmount(
                new BigDecimal("100000"), new BigDecimal("10"), 6);

        assertThat(total).isEqualByComparingTo(new BigDecimal("105000.00"));
    }

    @Test
    void calculateBulletRepayment_zeroRate_returnsPrincipal() {
        BigDecimal total = emiService.calculateBulletRepaymentAmount(
                new BigDecimal("50000"), BigDecimal.ZERO, 12);

        assertThat(total).isEqualByComparingTo(new BigDecimal("50000.00"));
    }

    // ── getDefaultRate ────────────────────────────────────────────

    @Test
    void getDefaultRate_personalFixed_returns12_50() {
        assertThat(emiService.getDefaultRate(Loan.LoanType.PERSONAL, Loan.InterestRateType.FIXED))
                .isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void getDefaultRate_homeFixed_returns8_75() {
        assertThat(emiService.getDefaultRate(Loan.LoanType.HOME, Loan.InterestRateType.FIXED))
                .isEqualByComparingTo(new BigDecimal("8.75"));
    }

    @Test
    void getDefaultRate_goldFixed_returns7_50() {
        assertThat(emiService.getDefaultRate(Loan.LoanType.GOLD, Loan.InterestRateType.FIXED))
                .isEqualByComparingTo(new BigDecimal("7.50"));
    }

    @Test
    void getDefaultRate_variableIsHalfPercentLowerThanFixed() {
        for (Loan.LoanType type : Loan.LoanType.values()) {
            BigDecimal fixed    = emiService.getDefaultRate(type, Loan.InterestRateType.FIXED);
            BigDecimal variable = emiService.getDefaultRate(type, Loan.InterestRateType.VARIABLE);
            assertThat(fixed.subtract(variable))
                    .as("variable rate for %s should be 0.5 below fixed", type)
                    .isEqualByComparingTo(new BigDecimal("0.50"));
        }
    }

    // ── getMaxEligibleAmount ─────────────────────────────────────

    @Test
    void getMaxEligibleAmount_home_fiveTimesIncome() {
        BigDecimal eligible = emiService.getMaxEligibleAmount(
                new BigDecimal("600000"), Loan.LoanType.HOME);
        assertThat(eligible).isEqualByComparingTo(new BigDecimal("3000000.00"));
    }

    @Test
    void getMaxEligibleAmount_mortgage_fourTimesIncome() {
        BigDecimal eligible = emiService.getMaxEligibleAmount(
                new BigDecimal("600000"), Loan.LoanType.MORTGAGE);
        assertThat(eligible).isEqualByComparingTo(new BigDecimal("2400000.00"));
    }

    @Test
    void getMaxEligibleAmount_business_threeTimesIncome() {
        BigDecimal eligible = emiService.getMaxEligibleAmount(
                new BigDecimal("600000"), Loan.LoanType.BUSINESS);
        assertThat(eligible).isEqualByComparingTo(new BigDecimal("1800000.00"));
    }

    @Test
    void getMaxEligibleAmount_personal_twoTimesIncome() {
        BigDecimal eligible = emiService.getMaxEligibleAmount(
                new BigDecimal("600000"), Loan.LoanType.PERSONAL);
        assertThat(eligible).isEqualByComparingTo(new BigDecimal("1200000.00"));
    }

    // ── calculatePenalty ─────────────────────────────────────────

    @Test
    void calculatePenalty_30Days_correctAmount() {
        // 10000 * 0.00067 * 30 = 201.00
        BigDecimal penalty = emiService.calculatePenalty(
                new BigDecimal("10000"), 30);
        assertThat(penalty).isEqualByComparingTo(new BigDecimal("201.00"));
    }

    @Test
    void calculatePenalty_zeroDays_returnsZero() {
        BigDecimal penalty = emiService.calculatePenalty(
                new BigDecimal("10000"), 0);
        assertThat(penalty).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculatePenalty_largerEmi_largerPenalty() {
        BigDecimal p1 = emiService.calculatePenalty(new BigDecimal("5000"), 10);
        BigDecimal p2 = emiService.calculatePenalty(new BigDecimal("10000"), 10);
        assertThat(p2).isGreaterThan(p1);
    }
}