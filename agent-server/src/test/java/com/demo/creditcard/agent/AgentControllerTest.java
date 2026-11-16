package com.demo.creditcard.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AgentTestConfiguration.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingChatModel recordingChatModel;

    @Test
    @DisplayName("經辦訊息經由 Agent 回覆")
    void shouldReplyThroughAgent() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"controller-reply","message":"請分析卡號 4000000000000002"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(RecordingChatModel.STUB_REPLY));
    }

    @Test
    @DisplayName("帶相同 conversationId 的連續請求共用對話記憶")
    void shouldKeepContextAcrossRequestsWithSameConversationId() throws Exception {
        postChat("""
                {"conversationId":"controller-memory","message":"請分析卡號 4000000000000003"}
                """);

        postChat("""
                {"conversationId":"controller-memory","message":"那它的旅遊登錄國家是哪裡？"}
                """);

        assertThat(recordingChatModel.lastPrompt().getInstructions().stream().map(Message::getText).toList())
                .anyMatch(text -> text.contains("4000000000000003"));
    }

    @Test
    @DisplayName("未帶 conversationId 時仍可正常回覆")
    void shouldReplyWithoutConversationId() throws Exception {
        postChat("""
                {"message":"御璽卡的核卡門檻是什麼？"}
                """).andExpect(status().isOk());
    }

    @Test
    @DisplayName("空白訊息回 400")
    void shouldRejectBlankMessage() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":" "}
                                """))
                .andExpect(status().isBadRequest());
    }

    private ResultActions postChat(String body) throws Exception {
        return mockMvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
