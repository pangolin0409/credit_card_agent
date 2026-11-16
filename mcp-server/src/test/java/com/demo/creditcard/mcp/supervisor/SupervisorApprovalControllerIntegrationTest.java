package com.demo.creditcard.mcp.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.demo.creditcard.mcp.TestcontainersConfiguration;
import com.demo.creditcard.mcp.cardapplication.ApprovalDraftRequest;
import com.demo.creditcard.mcp.cardapplication.CardApplicationTools;
import com.demo.creditcard.mcp.cardapplication.CardTier;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SupervisorApprovalControllerIntegrationTest {

    private static final String SUPERVISOR_BODY = """
            {"supervisorId":"SUPERVISOR_01"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardApplicationTools cardApplicationTools;

    @Autowired
    private JdbcClient jdbcClient;

    private int draftId;

    @BeforeEach
    void createPendingDraft() {
        cardApplicationTools.createApprovalDraft(new ApprovalDraftRequest("A100000004", CardTier.SIGNATURE, 150_000, "符合御璽卡"));
        draftId = jdbcClient.sql("SELECT draft_id FROM approval_drafts WHERE customer_id = 'A100000004'").query(Integer.class).single();
    }

    @Test
    @DisplayName("列出待審草稿單，包含申請人姓名")
    void shouldListPendingDrafts() throws Exception {
        mockMvc.perform(get("/api/supervisor/approval-drafts").param("status", "PENDING_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value("A100000004"))
                .andExpect(jsonPath("$[0].fullName").value("張雅婷"));
    }

    @Test
    @DisplayName("主管核准後發出正式卡片並寫入稽核")
    void shouldIssueCardOnApproval() throws Exception {
        review("approve", draftId, SUPERVISOR_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.cardNumber").value(matchesPattern("4999\\d{12}")));

        assertThat(count("""
                SELECT COUNT(*) FROM credit_cards
                 WHERE customer_id = 'A100000004' AND card_type = 'SIGNATURE' AND credit_limit = 150000 AND status = 'ACTIVE'
                """)).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT reviewed_by FROM approval_drafts WHERE draft_id = :draftId")
                .param("draftId", draftId).query(String.class).single()).isEqualTo("SUPERVISOR_01");
        assertThat(count("""
                SELECT COUNT(*) FROM audit_logs
                 WHERE target_id = 'A100000004' AND action_type = 'DRAFT_APPROVED' AND operator = 'SUPERVISOR_01'
                """)).isEqualTo(1);
    }

    @Test
    @DisplayName("重複核准回 409，且只發出一張卡")
    void shouldRejectSecondApproval() throws Exception {
        review("approve", draftId, SUPERVISOR_BODY).andExpect(status().isOk());

        review("approve", draftId, SUPERVISOR_BODY).andExpect(status().isConflict());

        assertThat(count("SELECT COUNT(*) FROM credit_cards")).isEqualTo(7);
    }

    @Test
    @DisplayName("主管駁回後不發卡，並寫入稽核")
    void shouldNotIssueCardOnRejection() throws Exception {
        review("reject", draftId, SUPERVISOR_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(count("SELECT COUNT(*) FROM credit_cards")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action_type = 'DRAFT_REJECTED'")).isEqualTo(1);
    }

    @Test
    @DisplayName("草稿單不存在時回 404")
    void shouldReturnNotFoundForUnknownDraft() throws Exception {
        review("approve", 999_999, SUPERVISOR_BODY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("999999")));
    }

    @Test
    @DisplayName("未填主管代號時回 400")
    void shouldRequireSupervisorId() throws Exception {
        review("approve", draftId, """
                {"supervisorId":""}
                """).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("允許 agent-server 頁面跨來源呼叫")
    void shouldAllowCorsFromAgentServer() throws Exception {
        mockMvc.perform(options("/api/supervisor/approval-drafts/1/approve")
                        .header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"));
    }

    private ResultActions review(String action, int targetDraftId, String body) throws Exception {
        return mockMvc.perform(post("/api/supervisor/approval-drafts/{draftId}/{action}", targetDraftId, action)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long count(String sql) {
        return jdbcClient.sql(sql).query(Long.class).single();
    }
}
