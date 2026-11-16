package com.demo.creditcard.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlashCommandTest {

    @Test
    @DisplayName("解析 skill 名稱與參數")
    void shouldParseSkillNameAndArgument() {
        assertThat(SlashCommand.parse("/核卡初審 A100000003")).contains(new SlashCommand("核卡初審", "A100000003"));
    }

    @Test
    @DisplayName("中文輸入法的全形斜線與全形空白也視為指令")
    void shouldParseFullWidthSlashAndSpaceFromChineseInputMethod() {
        assertThat(SlashCommand.parse("／核卡初審　A100000003")).contains(new SlashCommand("核卡初審", "A100000003"));
    }

    @Test
    @DisplayName("去除前後空白，參數內的空白保留")
    void shouldStripSurroundingWhitespaceButKeepInnerSpaces() {
        assertThat(SlashCommand.parse("  /規則問答 御璽卡門檻 是多少？ ")).contains(new SlashCommand("規則問答", "御璽卡門檻 是多少？"));
    }

    @Test
    @DisplayName("沒有參數時參數為空字串")
    void shouldUseEmptyArgumentWhenNoneGiven() {
        assertThat(SlashCommand.parse("/核卡初審")).contains(new SlashCommand("核卡初審", ""));
    }

    @Test
    @DisplayName("只輸入斜線時名稱與參數皆為空字串")
    void shouldParseBareSlashAsEmptyCommand() {
        assertThat(SlashCommand.parse("/")).contains(new SlashCommand("", ""));
    }

    @Test
    @DisplayName("不是斜線開頭的訊息不視為指令")
    void shouldNotParseFreeText() {
        assertThat(SlashCommand.parse("請審核 A100000003")).isEmpty();
    }
}
