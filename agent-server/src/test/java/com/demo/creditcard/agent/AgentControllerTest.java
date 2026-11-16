package com.demo.creditcard.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AgentTestConfiguration.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("經辦訊息經由 Agent 回覆")
    void shouldReplyThroughAgent() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"請分析卡號 4000000000000002"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(RecordingChatModel.STUB_REPLY));
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
}
