package com.demo.creditcard.mcp.clerk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.demo.creditcard.mcp.card.CardStatus;
import com.demo.creditcard.mcp.cardapplication.DraftStatus;
import com.demo.creditcard.mcp.fraudcontrol.FraudVerdict;

class ClerkStatusPolicyTest {

    private final ClerkStatusPolicy policy = new ClerkStatusPolicy();

    @Test
    @DisplayName("草稿已核准時，即使有婉拒紀錄也顯示已核卡")
    void shouldResolveApprovedWhenLatestDraftApprovedEvenIfDeclined() {
        var status = policy.resolveApplicationStatus(applicant(DraftStatus.APPROVED, true));

        assertThat(status).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    @DisplayName("草稿待審時顯示待主管放行")
    void shouldResolvePendingReviewWhenLatestDraftPending() {
        var status = policy.resolveApplicationStatus(applicant(DraftStatus.PENDING_REVIEW, false));

        assertThat(status).isEqualTo(ApplicationStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("草稿被主管退回時顯示主管退回")
    void shouldResolveSupervisorRejectedWhenLatestDraftRejected() {
        var status = policy.resolveApplicationStatus(applicant(DraftStatus.REJECTED, false));

        assertThat(status).isEqualTo(ApplicationStatus.SUPERVISOR_REJECTED);
    }

    @Test
    @DisplayName("沒有草稿但有婉拒紀錄時顯示已婉拒")
    void shouldResolveDeclinedWhenNoDraftAndDeclined() {
        var status = policy.resolveApplicationStatus(applicant(null, true));

        assertThat(status).isEqualTo(ApplicationStatus.DECLINED);
    }

    @Test
    @DisplayName("沒有草稿也沒有婉拒時顯示待初審")
    void shouldResolveAwaitingReviewWhenNothingRecorded() {
        var status = policy.resolveApplicationStatus(applicant(null, false));

        assertThat(status).isEqualTo(ApplicationStatus.AWAITING_REVIEW);
    }

    @Test
    @DisplayName("卡片已凍結時，即使有升級紀錄也顯示已凍結")
    void shouldResolveFrozenWhenCardTempFrozenEvenIfEscalated() {
        var status = policy.resolveCardHandlingStatus(card(CardStatus.TEMP_FROZEN, true));

        assertThat(status).isEqualTo(CardHandlingStatus.FROZEN);
    }

    @Test
    @DisplayName("卡片有升級紀錄時顯示已升級風控")
    void shouldResolveEscalatedWhenActiveCardEscalated() {
        var status = policy.resolveCardHandlingStatus(card(CardStatus.ACTIVE, true));

        assertThat(status).isEqualTo(CardHandlingStatus.ESCALATED);
    }

    @Test
    @DisplayName("卡片未處理時顯示待處理")
    void shouldResolveAwaitingActionWhenActiveCardNotHandled() {
        var status = policy.resolveCardHandlingStatus(card(CardStatus.ACTIVE, false));

        assertThat(status).isEqualTo(CardHandlingStatus.AWAITING_ACTION);
    }

    @Test
    @DisplayName("正常使用且無異常的卡片不列入警示")
    void shouldNotListActiveCardWithNoAnomaly() {
        assertThat(policy.isAlertCard(card(CardStatus.ACTIVE, false), FraudVerdict.NO_ANOMALY)).isFalse();
    }

    @Test
    @DisplayName("出國旅遊正常的卡片列入警示，讓經辦解除警報")
    void shouldListActiveCardWithNormalTravel() {
        assertThat(policy.isAlertCard(card(CardStatus.ACTIVE, false), FraudVerdict.NORMAL_TRAVEL)).isTrue();
    }

    @Test
    @DisplayName("已凍結的卡片列入警示並顯示處理結果")
    void shouldListFrozenCardEvenWithNoAnomaly() {
        assertThat(policy.isAlertCard(card(CardStatus.TEMP_FROZEN, false), FraudVerdict.NO_ANOMALY)).isTrue();
    }

    @Test
    @DisplayName("已升級風控的卡片列入警示並顯示處理結果")
    void shouldListEscalatedCardEvenWithNoAnomaly() {
        assertThat(policy.isAlertCard(card(CardStatus.ACTIVE, true), FraudVerdict.NO_ANOMALY)).isTrue();
    }

    @Test
    @DisplayName("只有待初審與主管退回的申請可以再送初審")
    void shouldAllowActionOnlyForAwaitingReviewAndSupervisorRejected() {
        assertThat(Arrays.stream(ApplicationStatus.values()).filter(ApplicationStatus::isActionable))
                .containsExactlyInAnyOrder(ApplicationStatus.AWAITING_REVIEW, ApplicationStatus.SUPERVISOR_REJECTED);
    }

    @Test
    @DisplayName("只有待處理的卡片可以送風險分析")
    void shouldAllowActionOnlyForAwaitingActionCards() {
        assertThat(Arrays.stream(CardHandlingStatus.values()).filter(CardHandlingStatus::isActionable))
                .containsExactly(CardHandlingStatus.AWAITING_ACTION);
    }

    @Test
    @DisplayName("規則引擎判定附上中文說明")
    void shouldLabelFraudVerdictInChinese() {
        assertThat(FraudVerdict.NORMAL_TRAVEL.label()).isEqualTo("出國旅遊正常，建議解除警報");
        assertThat(FraudVerdict.ESCALATE_TO_RISK_OFFICER.label()).isEqualTo("超高金額紅線，需升級風控");
        assertThat(FraudVerdict.OVERSEAS_FRAUD.label()).isEqualTo("疑似境外盜刷");
        assertThat(FraudVerdict.CARD_TESTING.label()).isEqualTo("疑似小額測卡");
        assertThat(FraudVerdict.NO_ANOMALY.label()).isEqualTo("無異常");
    }

    private static ApplicantSnapshot applicant(DraftStatus latestDraftStatus, boolean hasDecline) {
        return new ApplicantSnapshot("A100000003", "陳建宏", Optional.ofNullable(latestDraftStatus), hasDecline);
    }

    private static CardSnapshot card(CardStatus status, boolean hasEscalation) {
        return new CardSnapshot("4000000000000002", "陳大明", status, hasEscalation);
    }
}
