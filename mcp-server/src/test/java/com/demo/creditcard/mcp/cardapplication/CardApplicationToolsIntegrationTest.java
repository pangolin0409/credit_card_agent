package com.demo.creditcard.mcp.cardapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import com.demo.creditcard.mcp.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CardApplicationToolsIntegrationTest {

    @Autowired
    private CardApplicationTools cardApplicationTools;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("合格申請人取得頂級無限卡 50 萬的評估結果")
    void shouldReturnInfiniteAssessmentForQualifiedApplicant() {
        var result = cardApplicationTools.getApplicantProfile("A100000003");

        assertThat(result.applicant().fullName()).isEqualTo("陳建宏");
        assertThat(result.assessment().decision()).isEqualTo(ApplicationDecision.RECOMMEND_APPROVAL);
        assertThat(result.assessment().recommendedCardTier()).isEqualTo(CardTier.INFINITE);
        assertThat(result.assessment().recommendedCreditLimit()).isEqualTo(500_000);
        assertThat(result.assessment().remainingDebtCapacity()).isEqualTo(4_400_000);
    }

    @Test
    @DisplayName("負債受限的申請人額度被限縮為剩餘可貸空間")
    void shouldReturnCappedLimitForDebtConstrainedApplicant() {
        var assessment = cardApplicationTools.getApplicantProfile("A100000005").assessment();

        assertThat(assessment.recommendedCardTier()).isEqualTo(CardTier.SIGNATURE);
        assertThat(assessment.recommendedCreditLimit()).isEqualTo(120_000);
    }

    @Test
    @DisplayName("查無客戶時回報明確錯誤")
    void shouldThrowWhenCustomerNotFound() {
        assertThatThrownBy(() -> cardApplicationTools.getApplicantProfile("Z999999999"))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("Z999999999");
    }

    @Test
    @DisplayName("建立草稿單後狀態為待審，且不會寫入正式卡片")
    void shouldCreatePendingDraftWithoutIssuingCard() {
        var outcome = cardApplicationTools.createApprovalDraft(
                new ApprovalDraftRequest("A100000004", CardTier.SIGNATURE, 150_000, "符合御璽卡"));

        assertThat(outcome.accepted()).isTrue();
        assertThat(draftStatuses("A100000004")).containsExactly("PENDING_REVIEW");
        assertThat(count("SELECT COUNT(*) FROM credit_cards")).isEqualTo(6);
        assertThat(auditCount("A100000004", "APPROVAL_DRAFT", "AI_AGENT_MCP")).isEqualTo(1);
    }

    @Test
    @DisplayName("黑名單申請人建立草稿被護欄擋下")
    void shouldBlockDraftForBlacklistedApplicant() {
        var outcome = cardApplicationTools.createApprovalDraft(
                new ApprovalDraftRequest("A100000001", CardTier.INFINITE, 500_000, "高收入"));

        assertThat(outcome.accepted()).isFalse();
        assertThat(draftStatuses("A100000001")).isEmpty();
        assertThat(auditCount("A100000001", "GUARDRAIL_BLOCKED", "AI_AGENT_MCP")).isEqualTo(1);
    }

    @Test
    @DisplayName("草稿額度超過剩餘可貸空間被擋下")
    void shouldBlockDraftExceedingCappedLimit() {
        var outcome = cardApplicationTools.createApprovalDraft(
                new ApprovalDraftRequest("A100000005", CardTier.SIGNATURE, 150_000, "御璽卡"));

        assertThat(outcome.accepted()).isFalse();
        assertThat(draftStatuses("A100000005")).isEmpty();
    }

    @Test
    @DisplayName("草稿卡別高於規則建議卡別被擋下")
    void shouldBlockDraftWithMismatchedTier() {
        var outcome = cardApplicationTools.createApprovalDraft(
                new ApprovalDraftRequest("A100000006", CardTier.INFINITE, 500_000, "高收入"));

        assertThat(outcome.accepted()).isFalse();
        assertThat(draftStatuses("A100000006")).isEmpty();
    }

    @Test
    @DisplayName("同一申請人已有待審草稿時，不得再建立第二張")
    void shouldBlockDuplicatePendingDraft() {
        var request = new ApprovalDraftRequest("A100000004", CardTier.SIGNATURE, 150_000, "符合御璽卡");
        cardApplicationTools.createApprovalDraft(request);

        var secondOutcome = cardApplicationTools.createApprovalDraft(request);

        assertThat(secondOutcome.accepted()).isFalse();
        assertThat(draftStatuses("A100000004")).hasSize(1);
    }

    @Test
    @DisplayName("退件原因寫入稽核軌跡")
    void shouldRecordDecline() {
        var outcome = cardApplicationTools.declineApplication("A100000002", "負債比過高");

        assertThat(outcome.accepted()).isTrue();
        assertThat(jdbcClient.sql("SELECT reason FROM audit_logs WHERE target_id = 'A100000002' AND action_type = 'APPLICATION_DECLINE'")
                .query(String.class).list())
                .containsExactly("負債比過高");
    }

    private List<String> draftStatuses(String customerId) {
        return jdbcClient.sql("SELECT status FROM approval_drafts WHERE customer_id = :customerId")
                .param("customerId", customerId)
                .query(String.class)
                .list();
    }

    private long auditCount(String targetId, String actionType, String operator) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM audit_logs
                         WHERE target_id = :targetId AND action_type = :actionType AND operator = :operator
                        """)
                .param("targetId", targetId)
                .param("actionType", actionType)
                .param("operator", operator)
                .query(Long.class)
                .single();
    }

    private long count(String sql) {
        return jdbcClient.sql(sql).query(Long.class).single();
    }
}
