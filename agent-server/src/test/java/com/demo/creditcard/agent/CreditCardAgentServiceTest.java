package com.demo.creditcard.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
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
        creditCardAgentService.chat(newConversationId(), "請審核客戶 A100000003");

        assertThat(recordingChatModel.lastPrompt().getSystemMessage().getText())
                .contains("信用卡經辦 AI Agent 標準作業程序")
                .contains("【緊急警告】");
    }

    @Test
    @DisplayName("SOP 允許不帶編號的一般規則問答")
    void shouldAllowGeneralQuestionsInSop() {
        creditCardAgentService.chat(newConversationId(), "御璽卡的核卡門檻是什麼？");

        assertThat(recordingChatModel.lastPrompt().getSystemMessage().getText()).contains("## 一般問答");
    }

    @Test
    @DisplayName("只把白名單工具交給模型，主管放行工具不可見")
    void shouldPassOnlyAllowListedToolsToModel() {
        creditCardAgentService.chat(newConversationId(), "請分析卡號 4000000000000002");

        var options = (ToolCallingChatOptions) recordingChatModel.lastPrompt().getOptions();
        var toolNames = options.getToolCallbacks().stream().map(callback -> callback.getToolDefinition().name()).toList();
        assertThat(toolNames).containsExactlyInAnyOrderElementsOf(ToolAllowList.ALLOWED_TOOL_NAMES);
    }

    @Test
    @DisplayName("回傳模型的回覆內容")
    void shouldReturnModelReply() {
        assertThat(creditCardAgentService.chat(newConversationId(), "你好")).isEqualTo(RecordingChatModel.STUB_REPLY);
    }

    @Test
    @DisplayName("同一段對話中，追問時模型看得到先前提過的卡號")
    void shouldRememberEarlierMessagesInSameConversation() {
        var conversationId = newConversationId();
        creditCardAgentService.chat(conversationId, "請分析卡號 4000000000000002");

        creditCardAgentService.chat(conversationId, "那它為什麼被凍結？");

        assertThat(promptText()).contains("4000000000000002").contains(RecordingChatModel.STUB_REPLY);
    }

    @Test
    @DisplayName("不同對話之間的記憶互相隔離")
    void shouldIsolateConversations() {
        creditCardAgentService.chat(newConversationId(), "請分析卡號 4000000000000002");

        creditCardAgentService.chat(newConversationId(), "那它為什麼被凍結？");

        assertThat(promptText()).doesNotContain("4000000000000002");
    }

    private String promptText() {
        return recordingChatModel.lastPrompt().getInstructions().stream()
                .map(Message::getText)
                .reduce("", String::concat);
    }

    private static String newConversationId() {
        return UUID.randomUUID().toString();
    }
}
