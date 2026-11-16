package com.demo.creditcard.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillCatalogTest {

    @Test
    @DisplayName("skill 名稱重複時啟動失敗")
    void shouldFailWhenSkillNamesDuplicated() {
        var skills = List.of(agentSkill("核卡初審", Set.of(), List.of()), agentSkill("核卡初審", Set.of(), List.of()));

        assertThatThrownBy(() -> new SkillCatalog("common", skills))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("核卡初審");
    }

    @Test
    @DisplayName("skill 使用白名單以外的工具時啟動失敗")
    void shouldFailWhenSkillUsesToolOutsideAllowList() {
        var skills = List.of(agentSkill("核卡初審", Set.of("approveApprovalDraft"), List.of()));

        assertThatThrownBy(() -> new SkillCatalog("common", skills))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approveApprovalDraft");
    }

    @Test
    @DisplayName("skill 參照不存在的 skill 時啟動失敗")
    void shouldFailWhenReferenceUnknown() {
        var skills = List.of(agentSkill("規則問答", Set.of(), List.of("不存在")));

        assertThatThrownBy(() -> new SkillCatalog("common", skills))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不存在");
    }

    private static SkillDefinition agentSkill(String name, Set<String> toolNames, List<String> references) {
        return new SkillDefinition(name, SkillKind.AGENT, name, SkillArgument.TEXT, toolNames, references, false, "## " + name, null);
    }
}
