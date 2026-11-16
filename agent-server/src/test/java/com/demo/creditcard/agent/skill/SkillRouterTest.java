package com.demo.creditcard.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.demo.creditcard.agent.AgentTestConfiguration;
import com.demo.creditcard.agent.ToolAllowList;
import com.demo.creditcard.agent.skill.RoutingDecision.CallModel;
import com.demo.creditcard.agent.skill.RoutingDecision.ReplyDirectly;

@SpringBootTest
@ActiveProfiles("test")
@Import(AgentTestConfiguration.class)
class SkillRouterTest {

    @Autowired
    private SkillRouter skillRouter;

    @Test
    @DisplayName("/核卡初審 只載入核卡初審規則與場景一工具")
    void shouldScopePromptAndToolsWhenCardApplicationReviewInvoked() {
        var decision = (CallModel) skillRouter.route("/核卡初審 A100000003");

        assertThat(decision.toolNames()).containsExactlyInAnyOrder("getApplicantProfile", "createApprovalDraft", "declineApplication");
        assertThat(decision.systemPrompt())
                .contains("信用卡經辦 AI Agent 標準作業程序")
                .contains("## 核卡初審")
                .doesNotContain("## 刷卡風險分析");
    }

    @Test
    @DisplayName("/刷卡風險分析 只拿得到場景二工具")
    void shouldScopeToolsWhenCardRiskAnalysisInvoked() {
        var decision = (CallModel) skillRouter.route("/刷卡風險分析 4000000000000002");

        assertThat(decision.toolNames()).containsExactlyInAnyOrder("getCardRiskProfile", "freezeCard", "escalateToRiskOfficer");
    }

    @Test
    @DisplayName("/規則問答 沒有工具，但附上兩個場景的規則")
    void shouldGiveNoToolsButReferenceRulesWhenRuleInquiryInvoked() {
        var decision = (CallModel) skillRouter.route("/規則問答 御璽卡門檻？");

        assertThat(decision.toolNames()).isEmpty();
        assertThat(decision.systemPrompt()).contains("## 規則問答").contains("## 核卡初審").contains("## 刷卡風險分析");
    }

    @Test
    @DisplayName("自由提問維持全部 6 個工具，且不載入規則問答")
    void shouldKeepAllToolsWhenFreeText() {
        var decision = (CallModel) skillRouter.route("那它為什麼被凍結？");

        assertThat(decision.toolNames()).containsExactlyInAnyOrderElementsOf(ToolAllowList.ALLOWED_TOOL_NAMES);
        assertThat(decision.systemPrompt()).contains("## 核卡初審").contains("## 刷卡風險分析").doesNotContain("## 規則問答");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/核卡初審 陳", "/核卡初審 a100000003", "/核卡初審", "/刷卡風險分析 0002"})
    @DisplayName("對象不是完整編號時不呼叫 AI，請經辦從候選清單選取")
    void shouldReplyDirectlyWhenTargetUnresolved(String message) {
        assertThat(skillRouter.route(message)).isEqualTo(new ReplyDirectly("請從候選清單選取對象"));
    }

    @Test
    @DisplayName("/規則問答 沒有問題時請經辦輸入問題")
    void shouldAskForQuestionWhenRuleInquiryBlank() {
        assertThat(skillRouter.route("/規則問答")).isEqualTo(new ReplyDirectly("請在指令後輸入問題"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/不存在 A100000003", "/"})
    @DisplayName("查無指令時不呼叫 AI")
    void shouldReplyDirectlyWhenSkillUnknown(String message) {
        assertThat(skillRouter.route(message)).isEqualTo(new ReplyDirectly("查無此指令，請輸入 / 查看可用指令"));
    }

    @Test
    @DisplayName("清單指令送到對話時請經辦從選單選取")
    void shouldReplyDirectlyWhenListSkillSentToChat() {
        assertThat(skillRouter.route("/待審案件")).isEqualTo(new ReplyDirectly("此指令會顯示清單，請從選單選取"));
    }
}
