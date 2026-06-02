package com.rewabank.loans.service;

import com.rewabank.loans.entity.EmiSchedule;
import com.rewabank.loans.entity.Loan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EMI Calculator supporting:
 * - FIXED rate EMI (reducing balance — standard Indian banking)
 * - VARIABLE rate EMI (recalculates on rate change)
 * - BULLET repayment (principal + interest at maturity)
 *
 * EMI formula: P × r × (1+r)^n / ((1+r)^n - 1)
 * where r = monthly rate = annualRate / 12 / 100
 */
@Service
public class EmiCalculatorService {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    // ── EMI Calculation ───────────────────────────────────────────────────────

    public BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRatePercent,
                                   int tenureMonths) {
        if (annualRatePercent.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.HALF_UP);
        }

        BigDecimal r = annualRatePercent.divide(BigDecimal.valueOf(1200), MC);
        BigDecimal onePlusRPowN = BigDecimal.ONE.add(r, MC).pow(tenureMonths, MC);

        return principal.multiply(r, MC)
                .multiply(onePlusRPowN, MC)
                .divide(onePlusRPowN.subtract(BigDecimal.ONE, MC), 2, RoundingMode.HALF_UP);
    }

    // ── Generate EMI Schedule (FIXED rate) ────────────────────────────────────

    public List<EmiSchedule> generateEmiSchedule(UUID loanId, BigDecimal principal,
                                                 BigDecimal annualRatePercent,
                                                 int tenureMonths, LocalDate firstEmiDate) {
        BigDecimal emi         = calculateEmi(principal, annualRatePercent, tenureMonths);
        BigDecimal monthlyRate = annualRatePercent.divide(BigDecimal.valueOf(1200), MC);
        BigDecimal outstanding = principal;
        List<EmiSchedule> schedule = new ArrayList<>();

        for (int i = 1; i <= tenureMonths; i++) {
            BigDecimal interest   = outstanding.multiply(monthlyRate, MC)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal principal_ = (i == tenureMonths)
                    ? outstanding
                    : emi.subtract(interest);
            BigDecimal actualEmi  = (i == tenureMonths)
                    ? outstanding.add(interest)
                    : emi;
            outstanding = outstanding.subtract(principal_).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            schedule.add(EmiSchedule.builder()
                    .loanId(loanId)
                    .installmentNumber(i)
                    .dueDate(firstEmiDate.plusMonths(i - 1))
                    .emiAmount(actualEmi.setScale(2, RoundingMode.HALF_UP))
                    .principalPart(principal_.setScale(2, RoundingMode.HALF_UP))
                    .interestPart(interest)
                    .outstandingAfter(outstanding)
                    .status(EmiSchedule.EmiStatus.PENDING)
                    .build());
        }
        return schedule;
    }

    // ── Recalculate Remaining Schedule (VARIABLE rate change) ─────────────────

    public List<EmiSchedule> recalculateRemainingSchedule(UUID loanId,
                                                          BigDecimal outstandingPrincipal,
                                                          BigDecimal newAnnualRate,
                                                          int remainingMonths,
                                                          int startInstallmentNumber,
                                                          LocalDate startDate) {
        return generateEmiScheduleFrom(loanId, outstandingPrincipal, newAnnualRate,
                remainingMonths, startInstallmentNumber, startDate);
    }

    private List<EmiSchedule> generateEmiScheduleFrom(UUID loanId, BigDecimal principal,
                                                      BigDecimal annualRate, int months,
                                                      int startNum, LocalDate startDate) {
        BigDecimal emi         = calculateEmi(principal, annualRate, months);
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), MC);
        BigDecimal outstanding = principal;
        List<EmiSchedule> schedule = new ArrayList<>();

        for (int i = 0; i < months; i++) {
            BigDecimal interest   = outstanding.multiply(monthlyRate, MC)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal principal_ = (i == months - 1) ? outstanding : emi.subtract(interest);
            BigDecimal actualEmi  = (i == months - 1) ? outstanding.add(interest) : emi;
            outstanding = outstanding.subtract(principal_).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            schedule.add(EmiSchedule.builder()
                    .loanId(loanId)
                    .installmentNumber(startNum + i)
                    .dueDate(startDate.plusMonths(i))
                    .emiAmount(actualEmi.setScale(2, RoundingMode.HALF_UP))
                    .principalPart(principal_.setScale(2, RoundingMode.HALF_UP))
                    .interestPart(interest)
                    .outstandingAfter(outstanding)
                    .status(EmiSchedule.EmiStatus.PENDING)
                    .build());
        }
        return schedule;
    }

    // ── Bullet Repayment Calculation ─────────────────────────────────────────

    /**
     * BULLET: customer pays nothing until maturity.
     * Total repayment = principal + total interest (simple interest for GOLD/short-term)
     */
    public BigDecimal calculateBulletRepaymentAmount(BigDecimal principal,
                                                     BigDecimal annualRatePercent,
                                                     int tenureMonths) {
        // Simple interest for bullet: P * (1 + r*t)
        BigDecimal rate    = annualRatePercent.divide(BigDecimal.valueOf(100), MC);
        BigDecimal time    = BigDecimal.valueOf(tenureMonths)
                .divide(BigDecimal.valueOf(12), MC);
        BigDecimal interest = principal.multiply(rate, MC).multiply(time, MC)
                .setScale(2, RoundingMode.HALF_UP);
        return principal.add(interest);
    }

    // ── Interest Rate Defaults by Loan Type ───────────────────────────────────

    public BigDecimal getDefaultRate(Loan.LoanType type, Loan.InterestRateType rateType) {
        BigDecimal base = switch (type) {
            case PERSONAL  -> new BigDecimal("12.50");
            case HOME      -> new BigDecimal("8.75");
            case VEHICLE   -> new BigDecimal("9.25");
            case EDUCATION -> new BigDecimal("10.50");
            case BUSINESS  -> new BigDecimal("14.00");
            case GOLD      -> new BigDecimal("7.50");
            case MORTGAGE  -> new BigDecimal("9.00");
        };
        // Variable rate loans start 0.5% lower than fixed
        return rateType == Loan.InterestRateType.VARIABLE
                ? base.subtract(new BigDecimal("0.50"))
                : base;
    }

    // ── Eligibility ───────────────────────────────────────────────────────────

    public BigDecimal getMaxEligibleAmount(BigDecimal annualIncome, Loan.LoanType type) {
        BigDecimal multiplier = switch (type) {
            case HOME      -> new BigDecimal("5.0");
            case MORTGAGE  -> new BigDecimal("4.0");
            case BUSINESS  -> new BigDecimal("3.0");
            default        -> new BigDecimal("2.0");
        };
        return annualIncome.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    // ── Penalty Calculation (overdue EMI) ─────────────────────────────────────

    public BigDecimal calculatePenalty(BigDecimal emiAmount, int daysOverdue) {
        // 2% per month on overdue EMI = ~0.067% per day
        BigDecimal dailyPenaltyRate = new BigDecimal("0.00067");
        return emiAmount.multiply(dailyPenaltyRate, MC)
                .multiply(BigDecimal.valueOf(daysOverdue), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }
}