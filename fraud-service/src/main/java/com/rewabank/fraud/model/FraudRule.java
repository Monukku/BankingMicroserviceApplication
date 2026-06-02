package com.rewabank.fraud.model;

import lombok.*;

/**
 * Redis-backed fraud rule.
 * Rules are stored in Redis and evaluated in <100ms.
 * Each rule contributes a score to the total.
 * Total score determines action: APPROVE/FLAG/BLOCK.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FraudRule {

    private String ruleName;
    private int    scoreContribution;    // added to total score
    private String description;

    // Thresholds
    public static final int APPROVE_MAX = 49;
    public static final int FLAG_MAX    = 79;
    // Score >= 80 = BLOCK

    public static String actionFor(int totalScore) {
        if (totalScore <= APPROVE_MAX) return "APPROVE";
        if (totalScore <= FLAG_MAX)    return "FLAG";
        return "BLOCK";
    }
}
