package com.demo.creditcard.mcp.cardapplication;

import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class CardApplicationPolicy {

    private static final int DEBT_BURDEN_MULTIPLIER = 22;
    private static final int MIN_REMAINING_DEBT_CAPACITY = 100_000;
    private static final int INFINITE_MIN_MONTHLY_INCOME = 150_000;
    private static final int INFINITE_MIN_CREDIT_SCORE = 750;
    private static final int SIGNATURE_MIN_MONTHLY_INCOME = 50_000;
    private static final int SIGNATURE_MIN_CREDIT_SCORE = 650;

    public ApplicationAssessment assess(ApplicantProfile applicant) {
        int remainingDebtCapacity = calculateRemainingDebtCapacity(applicant);

        if (applicant.isAmlBlacklisted()) {
            return ApplicationAssessment.declined(ApplicationDecision.DECLINE_AML_BLACKLIST, remainingDebtCapacity,
                    "命中洗錢防制或內部黑名單，依規定直接退件。");
        }
        if (remainingDebtCapacity < MIN_REMAINING_DEBT_CAPACITY) {
            return ApplicationAssessment.declined(ApplicationDecision.DECLINE_DEBT_BURDEN, remainingDebtCapacity,
                    "月收入 %,d × 22 − 現有負債 %,d = 剩餘可貸空間 %,d，不足 100,000，負債比過高婉拒。"
                            .formatted(applicant.monthlyIncome(), applicant.existingUnsecuredDebt(), remainingDebtCapacity));
        }
        return determineCardTier(applicant)
                .map(cardTier -> recommend(cardTier, remainingDebtCapacity))
                .orElseGet(() -> ApplicationAssessment.declined(ApplicationDecision.DECLINE_BELOW_THRESHOLD, remainingDebtCapacity,
                        "月收入 %,d、聯徵分數 %d，未達御璽卡門檻（月收入 50,000、分數 650），予以婉拒。"
                                .formatted(applicant.monthlyIncome(), applicant.creditScore())));
    }

    public Optional<String> findDraftViolation(ApplicationAssessment assessment, CardTier requestedCardTier, int requestedCreditLimit) {
        if (!assessment.isRecommended()) {
            return Optional.of("規則判定為 %s，不得建立核卡草稿。".formatted(assessment.decision()));
        }
        if (requestedCardTier != assessment.recommendedCardTier()) {
            return Optional.of("申請卡別 %s 與規則建議卡別 %s 不符。".formatted(requestedCardTier, assessment.recommendedCardTier()));
        }
        if (requestedCreditLimit <= 0 || requestedCreditLimit > assessment.recommendedCreditLimit()) {
            return Optional.of("申請額度 %,d 必須介於 1 與規則建議額度 %,d 之間。"
                    .formatted(requestedCreditLimit, assessment.recommendedCreditLimit()));
        }
        return Optional.empty();
    }

    private int calculateRemainingDebtCapacity(ApplicantProfile applicant) {
        return applicant.monthlyIncome() * DEBT_BURDEN_MULTIPLIER - applicant.existingUnsecuredDebt();
    }

    private Optional<CardTier> determineCardTier(ApplicantProfile applicant) {
        if (applicant.monthlyIncome() >= INFINITE_MIN_MONTHLY_INCOME && applicant.creditScore() >= INFINITE_MIN_CREDIT_SCORE) {
            return Optional.of(CardTier.INFINITE);
        }
        if (applicant.monthlyIncome() >= SIGNATURE_MIN_MONTHLY_INCOME && applicant.creditScore() >= SIGNATURE_MIN_CREDIT_SCORE) {
            return Optional.of(CardTier.SIGNATURE);
        }
        return Optional.empty();
    }

    private ApplicationAssessment recommend(CardTier cardTier, int remainingDebtCapacity) {
        int creditLimit = Math.min(cardTier.maximumCreditLimit(), remainingDebtCapacity);
        return new ApplicationAssessment(ApplicationDecision.RECOMMEND_APPROVAL, cardTier, creditLimit, remainingDebtCapacity,
                "符合 %s 門檻，額度取卡別上限 %,d 與剩餘可貸空間 %,d 之較小值 = %,d。"
                        .formatted(cardTier, cardTier.maximumCreditLimit(), remainingDebtCapacity, creditLimit));
    }
}
