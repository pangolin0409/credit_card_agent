package com.demo.creditcard.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SkillFileParserTest {

    private final SkillFileParser parser = new SkillFileParser();

    @Test
    @DisplayName("解析 frontmatter 與內文，內文不含 frontmatter")
    void shouldParseFrontmatterAndInstructionsWhenFileValid() {
        var skill = parser.parse(new ClassPathResource("test-skills/valid.md"));

        assertThat(skill.name()).isEqualTo("測試技能");
        assertThat(skill.kind()).isEqualTo(SkillKind.AGENT);
        assertThat(skill.description()).isEqualTo("測試用的 skill");
        assertThat(skill.argument()).isEqualTo(SkillArgument.CUSTOMER_ID);
        assertThat(skill.toolNames()).containsExactlyInAnyOrder("getApplicantProfile", "declineApplication");
        assertThat(skill.references()).containsExactly("核卡初審");
        assertThat(skill.isIncludedInFreeChat()).isTrue();
        assertThat(skill.instructions()).startsWith("## 測試技能").contains("測試內文。").doesNotContain("---");
    }

    @Test
    @DisplayName("缺少必填欄位時啟動失敗，錯誤訊息包含檔名")
    void shouldFailWithFileNameWhenRequiredKeyMissing() {
        assertThatThrownBy(() -> parser.parse(new ClassPathResource("test-skills/missing-name.md")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-name.md");
    }
}
