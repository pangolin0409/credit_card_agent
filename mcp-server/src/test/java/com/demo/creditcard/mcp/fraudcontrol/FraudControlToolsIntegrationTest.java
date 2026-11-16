package com.demo.creditcard.mcp.fraudcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import com.demo.creditcard.mcp.TestcontainersConfiguration;
import com.demo.creditcard.mcp.card.CardNotFoundException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FraudControlToolsIntegrationTest {

    @Autowired
    private FraudControlTools fraudControlTools;

    @Autowired
    private JdbcClient jdbcClient;

    @ParameterizedTest(name = "卡號 {0} 判定為 {1}")
    @CsvSource({
            "4000000000000001, NORMAL_TRAVEL",
            "4000000000000002, OVERSEAS_FRAUD",
            "4000000000000003, OVERSEAS_FRAUD",
            "4000000000000004, CARD_TESTING",
            "4000000000000005, ESCALATE_TO_RISK_OFFICER",
            "4000000000000006, NO_ANOMALY"
    })
    @DisplayName("demo 卡片依規則判定")
    void shouldAssessDemoCards(String cardNumber, FraudVerdict expectedVerdict) {
        assertThat(fraudControlTools.getCardRiskProfile(cardNumber).assessment().verdict()).isEqualTo(expectedVerdict);
    }

    @Test
    @DisplayName("回傳近期交易與風險指標")
    void shouldExposeRiskMetrics() {
        var profile = fraudControlTools.getCardRiskProfile("4000000000000002");

        assertThat(profile.assessment().metrics().foreignOnlineAmountWithinOneHour()).isEqualTo(62_000);
        assertThat(profile.recentTransactions()).hasSize(4);
        assertThat(profile.card().fullName()).isEqualTo("陳大明");
    }

    @Test
    @DisplayName("查無卡號時回報明確錯誤")
    void shouldThrowWhenCardNotFound() {
        assertThatThrownBy(() -> fraudControlTools.getCardRiskProfile("4000000000009999"))
                .isInstanceOf(CardNotFoundException.class)
                .hasMessageContaining("4000000000009999");
    }

    @Test
    @DisplayName("沒有交易的新卡判定無異常")
    void shouldReturnNoAnomalyForCardWithoutTransactions() {
        jdbcClient.sql("""
                        INSERT INTO credit_cards (card_number, customer_id, card_type, credit_limit)
                        VALUES ('4000000000000099', 'B200000006', 'SIGNATURE', 150000)
                        """)
                .update();

        var profile = fraudControlTools.getCardRiskProfile("4000000000000099");

        assertThat(profile.assessment().verdict()).isEqualTo(FraudVerdict.NO_ANOMALY);
        assertThat(profile.recentTransactions()).isEmpty();
    }

    @Test
    @DisplayName("鎖卡後狀態為暫時凍結並寫入稽核")
    void shouldFreezeCardAndRecordAudit() {
        var outcome = fraudControlTools.freezeCard("4000000000000002", "境外盜刷");

        assertThat(outcome.accepted()).isTrue();
        assertThat(cardColumn("4000000000000002", "status")).isEqualTo("TEMP_FROZEN");
        assertThat(cardColumn("4000000000000002", "freeze_reason")).isEqualTo("境外盜刷");
        assertThat(auditCount("4000000000000002", "TEMP_FREEZE")).isEqualTo(1);
    }

    @Test
    @DisplayName("單筆超過 50 萬的卡片，鎖卡被護欄擋下")
    void shouldBlockFreezeOnEscalationCase() {
        var outcome = fraudControlTools.freezeCard("4000000000000005", "疑似盜刷");

        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.message()).contains("escalateToRiskOfficer");
        assertThat(cardColumn("4000000000000005", "status")).isEqualTo("ACTIVE");
        assertThat(auditCount("4000000000000005", "GUARDRAIL_BLOCKED")).isEqualTo(1);
    }

    @Test
    @DisplayName("已凍結的卡片不可重複凍結")
    void shouldBlockFreezeOnAlreadyFrozenCard() {
        fraudControlTools.freezeCard("4000000000000004", "測卡攻擊");

        var secondOutcome = fraudControlTools.freezeCard("4000000000000004", "測卡攻擊");

        assertThat(secondOutcome.accepted()).isFalse();
        assertThat(auditCount("4000000000000004", "TEMP_FREEZE")).isEqualTo(1);
    }

    @Test
    @DisplayName("升級風控主管只寫入稽核，不改變卡片狀態")
    void shouldRecordEscalationWithoutChangingStatus() {
        var outcome = fraudControlTools.escalateToRiskOfficer("4000000000000005", "單筆 680,000 CH 網路交易");

        assertThat(outcome.accepted()).isTrue();
        assertThat(auditCount("4000000000000005", "ESCALATION")).isEqualTo(1);
        assertThat(cardColumn("4000000000000005", "status")).isEqualTo("ACTIVE");
    }

    private String cardColumn(String cardNumber, String column) {
        return jdbcClient.sql("SELECT " + column + " FROM credit_cards WHERE card_number = :cardNumber")
                .param("cardNumber", cardNumber)
                .query(String.class)
                .single();
    }

    private long auditCount(String targetId, String actionType) {
        return jdbcClient.sql("SELECT COUNT(*) FROM audit_logs WHERE target_id = :targetId AND action_type = :actionType")
                .param("targetId", targetId)
                .param("actionType", actionType)
                .query(Long.class)
                .single();
    }
}
