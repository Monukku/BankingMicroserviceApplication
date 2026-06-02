package com.rewabank.loans.entity;

/**
 * Minimal Loan entity placeholder used by service calculations.
 *
 * This file provides the enums referenced by `EmiCalculatorService`.
 * If your project has a richer Loan domain model, replace or extend this class
 * accordingly.
 */
public class Loan {

    // Loan types used across the service layer
    public enum LoanType {
        PERSONAL, HOME, VEHICLE, EDUCATION, BUSINESS, GOLD, MORTGAGE
    }

    // Interest rate classification
    public enum InterestRateType {
        FIXED, VARIABLE
    }

    // Intentionally minimal: add fields/methods as needed by the rest of the application
}

