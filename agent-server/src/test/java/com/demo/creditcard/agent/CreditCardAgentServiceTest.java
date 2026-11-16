package com.demo.creditcard.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(AgentTestConfiguration.class)
class CreditCardAgentServiceTest {

    @Autowired
    private CreditCardAgentService creditCardAgentService;

    @Autowired
    private RecordingChatModel recordingChatModel;

    @Test
    @DisplayName("SOP Skill 載入為 system prompt")
    void shouldLoadSopIntoSystemPrompt() {
        creditCardAgentService.chat("請審核客戶 A100000003");

        assertThat(recordingChatModel.lastPrompt().getSystemMessage().getText())
                .contains("信用卡經辦 AI Agent 標準作業程序")
                .contains("【緊急警告】");
    }

    @Test
    @DisplayName("只把白名單工具交給模型，主管放行工具不可見")
    void shouldPassOnlyAllowListedToolsToModel() {
        creditCardAgentService.chat("請分析卡號 4000000000000002");

        var options = (ToolCallingChatOptions) recordingChatModel.lastPrompt().getOptions();
        var toolNames = options.getToolCallbacks().stream().map(callback -> callback.getToolDefinition().name()).toList();
        assertThat(toolNames).containsExactlyInAnyOrderElementsOf(ToolAllowList.ALLOWED_TOOL_NAMES);
    }

    @Test
    @DisplayName("回傳模型的回覆內容")
    void shouldReturnModelReply() {
        assertThat(creditCardAgentService.chat("你好")).isEqualTo(RecordingChatModel.STUB_REPLY);
    }
}
