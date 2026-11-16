package com.demo.creditcard.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.demo.creditcard.agent.AgentTestConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@Import(AgentTestConfiguration.class)
class SkillCatalogLoadingTest {

    @Autowired
    private SkillCatalog skillCatalog;

    @Test
    @DisplayName("依序載入 2 個清單指令與 3 個 AI skill")
    void shouldLoadFiveSkillsInMenuOrder() {
        assertThat(skillCatalog.all()).extracting(SkillDefinition::name)
                .containsExactly("待審案件", "警示卡片", "核卡初審", "刷卡風險分析", "規則問答");
    }

    @Test
    @DisplayName("核卡初審只拿得到場景一的工具")
    void shouldScopeCardApplicationReviewToScenarioOneTools() {
        assertThat(skillCatalog.find("核卡初審").orElseThrow().toolNames())
                .containsExactlyInAnyOrder("getApplicantProfile", "createApprovalDraft", "declineApplication");
    }

    @Test
    @DisplayName("刷卡風險分析只拿得到場景二的工具")
    void shouldScopeCardRiskAnalysisToScenarioTwoTools() {
        assertThat(skillCatalog.find("刷卡風險分析").orElseThrow().toolNames())
                .containsExactlyInAnyOrder("getCardRiskProfile", "freezeCard", "escalateToRiskOfficer");
    }

    @Test
    @DisplayName("規則問答拿不到任何工具")
    void shouldGiveRuleInquiryNoTools() {
        assertThat(skillCatalog.find("規則問答").orElseThrow().toolNames()).isEmpty();
    }

    @Test
    @DisplayName("自由提問只載入兩個場景的 skill")
    void shouldIncludeOnlyScenarioSkillsInFreeChat() {
        assertThat(skillCatalog.freeChatSkills()).extracting(SkillDefinition::name).containsExactly("核卡初審", "刷卡風險分析");
    }

    @Test
    @DisplayName("共用規則保留 SOP 標題、一般問答與紅線警告格式")
    void shouldKeepSopEssentialsInCommonInstructions() {
        assertThat(skillCatalog.commonInstructions())
                .contains("信用卡經辦 AI Agent 標準作業程序")
                .contains("## 一般問答")
                .contains("【緊急警告】")
                .contains("## 斜線指令");
    }
}
