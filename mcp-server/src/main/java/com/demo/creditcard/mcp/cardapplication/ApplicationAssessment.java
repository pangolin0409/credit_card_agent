package com.demo.creditcard.mcp.cardapplication;

public record ApplicationAssessment(
        ApplicationDecision decision,
        CardTier recommendedCardTier,
        int recommendedCreditLimit,
        int remainingDebtCapacity,
        String rationale) {

    static ApplicationAssessment declined(ApplicationDecision decision, int remainingDebtCapacity, String rationale) {
        return new ApplicationAssessment(decision, null, 0, remainingDebtCapacity, rationale);
    }

    boolean isRecommended() {
        return decision == ApplicationDecision.RECOMMEND_APPROVAL;
    }
}
