package com.demo.creditcard.agent.skill;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.demo.creditcard.agent.AgentTestConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AgentTestConfiguration.class)
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("回傳斜線選單需要的 skill 清單，不含 SOP 內文")
    void shouldListSkillsForSlashMenuWithoutInstructions() throws Exception {
        mockMvc.perform(get("/api/agent/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].name").value("待審案件"))
                .andExpect(jsonPath("$[0].kind").value("LIST"))
                .andExpect(jsonPath("$[0].source").value("/api/clerk/applications"))
                .andExpect(jsonPath("$[0].argument").doesNotExist())
                .andExpect(jsonPath("$[1].source").value("/api/clerk/alert-cards"))
                .andExpect(jsonPath("$[2].name").value("核卡初審"))
                .andExpect(jsonPath("$[2].kind").value("AGENT"))
                .andExpect(jsonPath("$[2].argument").value("customerId"))
                .andExpect(jsonPath("$[3].argument").value("cardNumber"))
                .andExpect(jsonPath("$[4].argument").value("text"))
                .andExpect(jsonPath("$[2].instructions").doesNotExist())
                .andExpect(jsonPath("$[2].toolNames").doesNotExist());
    }
}
