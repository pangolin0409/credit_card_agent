package com.demo.creditcard.mcp.cardapplication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardApplicationPolicyTest {

    private final CardApplicationPolicy policy = new CardApplicationPolicy();

    @Test
    @DisplayName("命中黑名單時，無論收入與分數都直接退件")
    void shouldDeclineBlacklistedApplicantRegardlessOfIncome() {
        var assessment = policy.assess(applicant(300_000, 0, 800, true));

        assertThat(assessment.decision()).isEqualTo(ApplicationDecision.DECLINE_AML_BLACKLIST);
        assertThat(assessment.recommendedCardTier()).isNull();
        assertThat(assessment.recommendedCreditLimit()).isZero();
    }

    @Test
    @DisplayName("月收入 × 22 − 負債不足 10 萬時，以負債比過高婉拒")
    void shouldDeclineWhenRemainingDebtCapacityBelowMinimum() {
        var assessment = policy.assess(applicant(60_000, 1_250_000, 720, false));

        assertThat(assessment.decision()).isEqualTo(ApplicationDecision.DECLINE_DEBT_BURDEN);
        assertThat(assessment.remainingDebtCapacity()).isEqualTo(70_000);
    }

    @Test
    @DisplayName("剩餘可貸空間 99,999 元時婉拒")
    void shouldDeclineWhenRemainingDebtCapacityIsOneBelowMinimum() {
        var assessment = policy.assess(applicant(50_000, 1_000_001, 700, false));

        assertThat(assessment.decision()).isEqualTo(ApplicationDecision.DECLINE_DEBT_BURDEN);
    }

    @Test
    @DisplayName("剩餘可貸空間剛好 10 萬時不婉拒，額度以 10 萬為上限")
    void shouldRecommendWhenRemainingDebtCapacityExactlyMinimum() {
        var assessment = policy.assess(applicant(50_000, 1_000_000, 700, false));

        assertRecommendation(assessment, CardTier.SIGNATURE, 100_000);
    }

    @Test
    @DisplayName("月收入 15 萬且分數 750 剛好達標，核發頂級無限卡 50 萬")
    void shouldRecommendInfiniteAtExactThreshold() {
        assertRecommendation(policy.assess(applicant(150_000, 0, 750, false)), CardTier.INFINITE, 500_000);
    }

    @Test
    @DisplayName("分數 749 未達無限卡門檻，改核御璽卡")
    void shouldRecommendSignatureWhenScoreBelowInfiniteThreshold() {
        assertRecommendation(policy.assess(applicant(200_000, 0, 749, false)), CardTier.SIGNATURE, 150_000);
    }

    @Test
    @DisplayName("月收入 149,999 未達無限卡門檻，改核御璽卡")
    void shouldRecommendSignatureWhenIncomeBelowInfiniteThreshold() {
        assertRecommendation(policy.assess(applicant(149_999, 0, 800, false)), CardTier.SIGNATURE, 150_000);
    }

    @Test
    @DisplayName("月收入 5 萬且分數 650 剛好達標，核發御璽卡 15 萬")
    void shouldRecommendSignatureAtExactThreshold() {
        assertRecommendation(policy.assess(applicant(50_000, 0, 650, false)), CardTier.SIGNATURE, 150_000);
    }

    @Test
    @DisplayName("卡別額度超過剩餘可貸空間時，取兩者較小值")
    void shouldCapCreditLimitByRemainingDebtCapacity() {
        assertRecommendation(policy.assess(applicant(60_000, 1_200_000, 680, false)), CardTier.SIGNATURE, 120_000);
    }

    @Test
    @DisplayName("月收入 49,999 未達御璽卡門檻時婉拒")
    void shouldDeclineWhenIncomeBelowSignatureThreshold() {
        assertThat(policy.assess(applicant(49_999, 0, 700, false)).decision())
                .isEqualTo(ApplicationDecision.DECLINE_BELOW_THRESHOLD);
    }

    @Test
    @DisplayName("分數 649 未達御璽卡門檻時婉拒")
    void shouldDeclineWhenScoreBelowSignatureThreshold() {
        assertThat(policy.assess(applicant(80_000, 0, 649, false)).decision())
                .isEqualTo(ApplicationDecision.DECLINE_BELOW_THRESHOLD);
    }

    @Test
    @DisplayName("黑名單申請人不得建立草稿")
    void shouldFindViolationForDeclinedAssessment() {
        var assessment = policy.assess(applicant(300_000, 0, 800, true));

        assertThat(policy.findDraftViolation(assessment, CardTier.INFINITE, 500_000)).isPresent();
    }

    @Test
    @DisplayName("草稿額度超過規則建議額度時違規")
    void shouldFindViolationWhenCreditLimitExceedsRecommendation() {
        var assessment = policy.assess(applicant(60_000, 1_200_000, 680, false));

        assertThat(policy.findDraftViolation(assessment, CardTier.SIGNATURE, 150_000)).isPresent();
    }

    @Test
    @DisplayName("草稿卡別與規則建議卡別不符時違規")
    void shouldFindViolationWhenCardTierMismatches() {
        var assessment = policy.assess(applicant(80_000, 0, 700, false));

        assertThat(policy.findDraftViolation(assessment, CardTier.INFINITE, 500_000)).isPresent();
    }

    @Test
    @DisplayName("草稿額度必須大於 0")
    void shouldFindViolationWhenCreditLimitIsNotPositive() {
        var assessment = policy.assess(applicant(80_000, 0, 700, false));

        assertThat(policy.findDraftViolation(assessment, CardTier.SIGNATURE, 0)).isPresent();
    }

    @Test
    @DisplayName("草稿符合規則建議時無違規")
    void shouldAcceptDraftMatchingRecommendation() {
        var assessment = policy.assess(applicant(80_000, 0, 700, false));

        assertThat(policy.findDraftViolation(assessment, CardTier.SIGNATURE, 150_000)).isEmpty();
    }

    private static ApplicantProfile applicant(int monthlyIncome, int existingUnsecuredDebt, int creditScore, boolean isAmlBlacklisted) {
        return new ApplicantProfile("A000000000", "測試申請人", monthlyIncome, existingUnsecuredDebt, creditScore, isAmlBlacklisted);
    }

    private static void assertRecommendation(ApplicationAssessment assessment, CardTier expectedTier, int expectedLimit) {
        assertThat(assessment.decision()).isEqualTo(ApplicationDecision.RECOMMEND_APPROVAL);
        assertThat(assessment.recommendedCardTier()).isEqualTo(expectedTier);
        assertThat(assessment.recommendedCreditLimit()).isEqualTo(expectedLimit);
    }
}
