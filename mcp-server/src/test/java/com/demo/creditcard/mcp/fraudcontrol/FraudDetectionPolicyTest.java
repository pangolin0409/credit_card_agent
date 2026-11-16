package com.demo.creditcard.mcp.fraudcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.demo.creditcard.mcp.card.CardRiskSubject;
import com.demo.creditcard.mcp.card.CardStatus;

class FraudDetectionPolicyTest {

    private static final LocalDateTime LATEST = LocalDateTime.of(2026, 11, 16, 12, 0);

    private final FraudDetectionPolicy policy = new FraudDetectionPolicy();
    private final AtomicInteger sequence = new AtomicInteger();

    @Test
    @DisplayName("單筆超過 50 萬時升級風控主管")
    void shouldEscalateWhenSingleAmountExceedsThreshold() {
        var assessment = policy.assess(subject(null), List.of(transaction(0, 500_001, "CH", true)));

        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.ESCALATE_TO_RISK_OFFICER);
        assertThat(assessment.metrics().maxSingleAmount()).isEqualTo(500_001);
    }

    @Test
    @DisplayName("單筆剛好 50 萬不觸發紅線")
    void shouldNotEscalateAtExactThreshold() {
        var assessment = policy.assess(subject(null), List.of(transaction(0, 500_000, "TW", false)));

        assertThat(assessment.verdict()).isNotEqualTo(FraudVerdict.ESCALATE_TO_RISK_OFFICER);
    }

    @Test
    @DisplayName("紅線優先於境外盜刷判定")
    void shouldEscalateBeforeOverseasFraud() {
        var assessment = policy.assess(subject(null), List.of(transaction(0, 680_000, "CH", true)));

        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.ESCALATE_TO_RISK_OFFICER);
    }

    @Test
    @DisplayName("刷卡國家與登錄旅遊國家相符時判定正常出國消費")
    void shouldTreatMatchingTravelCountryAsNormal() {
        var assessment = policy.assess(subject("JP"), List.of(
                transaction(40, 85_000, "JP", false),
                transaction(0, 42_000, "JP", false)));

        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.NORMAL_TRAVEL);
    }

    @Test
    @DisplayName("有旅遊登錄但只有國內交易時，不套用旅遊規則")
    void shouldIgnoreTravelNoticeWhenNoForeignTransactions() {
        var assessment = policy.assess(subject("JP"), List.of(transaction(0, 1_200, "TW", false)));

        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.NO_ANOMALY);
    }

    @Test
    @DisplayName("未登錄出國，1 小時內境外網路消費超過 5 萬判定盜刷")
    void shouldFlagOverseasFraudWhenNoTravelNotice() {
        var assessment = policy.assess(subject(null), List.of(
                transaction(45, 18_000, "US", true),
                transaction(25, 21_000, "US", true),
                transaction(0, 23_000, "US", true)));

        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.OVERSEAS_FRAUD);
        assertThat(assessment.metrics().foreignOnlineAmountWithinOneHour()).isEqualTo(62_000);
    }

    @Test
    @DisplayName("登錄國家與刷卡國家不符時視同未登錄")
    void shouldFlagOverseasFraudWhenTravelCountryMismatches() {
        var assessment = policy.assess(subject("JP"), List.of(
                transaction(30, 30_000, "FR", true),
                transaction(0, 25_000, "FR", true)));

        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.OVERSEAS_FRAUD);
        assertThat(assessment.metrics().foreignOnlineAmountWithinOneHour()).isEqualTo(55_000);
    }

    @Test
    @DisplayName("境外網路消費剛好 5 萬不判定盜刷")
    void shouldNotFlagOverseasFraudAtExactThreshold() {
        var assessment = policy.assess(subject(null), List.of(
                transaction(10, 20_000, "US", true),
                transaction(0, 30_000, "US", true)));

        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.NO_ANOMALY);
    }

    @Test
    @DisplayName("超過 1 小時的交易不列入境外消費累計")
    void shouldExcludeTransactionsOlderThanOneHour() {
        var assessment = policy.assess(subject(null), List.of(
                transaction(61, 30_000, "US", true),
                transaction(0, 30_000, "US", true)));

        assertThat(assessment.metrics().foreignOnlineAmountWithinOneHour()).isEqualTo(30_000);
        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.NO_ANOMALY);
    }

    @Test
    @DisplayName("剛好 1 小時前的交易列入境外消費累計")
    void shouldIncludeTransactionExactlyOneHourBefore() {
        var assessment = policy.assess(subject(null), List.of(
                transaction(60, 30_000, "US", true),
                transaction(0, 30_000, "US", true)));

        assertThat(assessment.metrics().foreignOnlineAmountWithinOneHour()).isEqualTo(60_000);
        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.OVERSEAS_FRAUD);
    }

    @Test
    @DisplayName("10 分鐘內 3 筆 50 元以下交易判定測卡攻擊")
    void shouldFlagCardTestingWithThreeMicroTransactions() {
        var assessment = policy.assess(subject(null), List.of(
                transaction(8, 1, "TW", true),
                transaction(4, 5, "TW", true),
                transaction(0, 50, "TW", true)));

        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.CARD_TESTING);
        assertThat(assessment.metrics().microTransactionCountWithinTenMinutes()).isEqualTo(3);
    }

    @Test
    @DisplayName("只有 2 筆 50 元以下交易時不判定測卡，51 元不列入")
    void shouldNotFlagCardTestingWithTwoMicroTransactions() {
        var assessment = policy.assess(subject(null), List.of(
                transaction(6, 5, "TW", true),
                transaction(3, 51, "TW", true),
                transaction(0, 50, "TW", true)));

        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.NO_ANOMALY);
        assertThat(assessment.metrics().microTransactionCountWithinTenMinutes()).isEqualTo(2);
    }

    @Test
    @DisplayName("超過 10 分鐘的小額交易不列入測卡計數")
    void shouldExcludeMicroTransactionsOlderThanTenMinutes() {
        var assessment = policy.assess(subject(null), List.of(
                transaction(11, 1, "TW", true),
                transaction(5, 5, "TW", true),
                transaction(0, 30, "TW", true)));

        assertThat(assessment.metrics().microTransactionCountWithinTenMinutes()).isEqualTo(2);
    }

    @Test
    @DisplayName("沒有任何交易時判定無異常，指標皆為 0")
    void shouldReturnNoAnomalyForEmptyTransactions() {
        var assessment = policy.assess(subject(null), List.of());

        assertThat(assessment.verdict()).isEqualTo(FraudVerdict.NO_ANOMALY);
        assertThat(assessment.metrics()).isEqualTo(new RiskMetrics(0, 0, 0));
    }

    private static CardRiskSubject subject(String travelCountry) {
        return new CardRiskSubject("4000000000000000", "B000000000", "測試持卡人", CardStatus.ACTIVE,
                travelCountry != null, travelCountry);
    }

    private TransactionRecord transaction(int minutesBeforeLatest, int amount, String countryCode, boolean isOnline) {
        return new TransactionRecord("TX" + sequence.incrementAndGet(), LATEST.minusMinutes(minutesBeforeLatest), amount,
                "Test Merchant", countryCode, isOnline);
    }
}
