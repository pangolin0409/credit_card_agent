package com.demo.creditcard.mcp.clerk;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.demo.creditcard.mcp.TestcontainersConfiguration;
import com.demo.creditcard.mcp.cardapplication.ApprovalDraftRequest;
import com.demo.creditcard.mcp.cardapplication.CardApplicationTools;
import com.demo.creditcard.mcp.cardapplication.CardTier;
import com.demo.creditcard.mcp.fraudcontrol.FraudControlTools;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ClerkQueryControllerIntegrationTest {

    private static final String AGENT_SERVER_ORIGIN = "http://localhost:8080";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardApplicationTools cardApplicationTools;

    @Autowired
    private FraudControlTools fraudControlTools;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("剛啟動時列出 7 位待初審的申請人，不含既有持卡人")
    void shouldListAllSevenApplicantsAwaitingReviewWhenFreshlyStarted() throws Exception {
        mockMvc.perform(get("/api/clerk/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[0].customerId").value("A100000001"))
                .andExpect(jsonPath("$[0].fullName").value("王志明"))
                .andExpect(jsonPath("$[0].statusLabel").value("待初審"))
                .andExpect(jsonPath("$[0].actionable").value(true))
                .andExpect(jsonPath("$[*].status", everyItem(is("AWAITING_REVIEW"))))
                .andExpect(jsonPath("$[*].customerId", everyItem(not(startsWith("B2")))));
    }

    @Test
    @DisplayName("建立草稿後顯示待主管放行，且不能再送初審")
    void shouldShowPendingReviewWhenDraftCreated() throws Exception {
        createInfiniteDraftFor("A100000003");

        mockMvc.perform(get("/api/clerk/applications"))
                .andExpect(jsonPath("$[2].customerId").value("A100000003"))
                .andExpect(jsonPath("$[2].status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$[2].actionable").value(false));
    }

    @Test
    @DisplayName("主管核准後申請人留在清單上，顯示已核卡")
    void shouldKeepApprovedApplicantInListWhenSupervisorApproves() throws Exception {
        supervisorReview("approve", createInfiniteDraftFor("A100000003"));

        mockMvc.perform(get("/api/clerk/applications"))
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[2].status").value("APPROVED"))
                .andExpect(jsonPath("$[2].statusLabel").value("已核卡"));
    }

    @Test
    @DisplayName("主管退回後可以再送初審")
    void shouldAllowReviewAgainWhenSupervisorRejects() throws Exception {
        supervisorReview("reject", createInfiniteDraftFor("A100000003"));

        mockMvc.perform(get("/api/clerk/applications"))
                .andExpect(jsonPath("$[2].status").value("SUPERVISOR_REJECTED"))
                .andExpect(jsonPath("$[2].actionable").value(true));
    }

    @Test
    @DisplayName("婉拒後顯示已婉拒")
    void shouldShowDeclinedWhenApplicationDeclined() throws Exception {
        cardApplicationTools.declineApplication("A100000007", "未達門檻");

        mockMvc.perform(get("/api/clerk/applications"))
                .andExpect(jsonPath("$[6].customerId").value("A100000007"))
                .andExpect(jsonPath("$[6].status").value("DECLINED"))
                .andExpect(jsonPath("$[6].statusLabel").value("已婉拒"));
    }

    @Test
    @DisplayName("警示卡片列出 0001 到 0005，不含正常消費的 0006")
    void shouldListFiveAlertCardsExcludingNormalCard() throws Exception {
        mockMvc.perform(get("/api/clerk/alert-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cardNumber", contains(
                        "4000000000000001", "4000000000000002", "4000000000000003", "4000000000000004", "4000000000000005")))
                .andExpect(jsonPath("$[*].verdict", contains(
                        "NORMAL_TRAVEL", "OVERSEAS_FRAUD", "OVERSEAS_FRAUD", "CARD_TESTING", "ESCALATE_TO_RISK_OFFICER")))
                .andExpect(jsonPath("$[0].last4").value("0001"))
                .andExpect(jsonPath("$[0].holderName").value("林小旅"))
                .andExpect(jsonPath("$[0].verdictLabel").value("出國旅遊正常，建議解除警報"))
                .andExpect(jsonPath("$[0].status").value("AWAITING_ACTION"))
                .andExpect(jsonPath("$[0].actionable").value(true));
    }

    @Test
    @DisplayName("凍結後顯示已凍結，且不能再送分析")
    void shouldShowFrozenWhenCardFrozen() throws Exception {
        fraudControlTools.freezeCard("4000000000000002", "境外盜刷");

        mockMvc.perform(get("/api/clerk/alert-cards"))
                .andExpect(jsonPath("$[1].cardNumber").value("4000000000000002"))
                .andExpect(jsonPath("$[1].status").value("FROZEN"))
                .andExpect(jsonPath("$[1].statusLabel").value("已凍結"))
                .andExpect(jsonPath("$[1].actionable").value(false));
    }

    @Test
    @DisplayName("升級風控後顯示已升級風控")
    void shouldShowEscalatedWhenCaseEscalated() throws Exception {
        fraudControlTools.escalateToRiskOfficer("4000000000000005", "紅線");

        mockMvc.perform(get("/api/clerk/alert-cards"))
                .andExpect(jsonPath("$[4].cardNumber").value("4000000000000005"))
                .andExpect(jsonPath("$[4].status").value("ESCALATED"))
                .andExpect(jsonPath("$[4].statusLabel").value("已升級風控"));
    }

    @Test
    @DisplayName("允許 agent-server 頁面跨來源讀取經辦清單")
    void shouldAllowCorsGetFromAgentServer() throws Exception {
        mockMvc.perform(options("/api/clerk/applications")
                        .header("Origin", AGENT_SERVER_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Origin", AGENT_SERVER_ORIGIN));
    }

    @Test
    @DisplayName("經辦 API 不允許跨來源 POST")
    void shouldRejectCorsPostOnClerkApi() throws Exception {
        mockMvc.perform(options("/api/clerk/applications")
                        .header("Origin", AGENT_SERVER_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private int createInfiniteDraftFor(String customerId) {
        cardApplicationTools.createApprovalDraft(new ApprovalDraftRequest(customerId, CardTier.INFINITE, 500_000, "符合無限卡"));
        return jdbcClient.sql("SELECT MAX(draft_id) FROM approval_drafts WHERE customer_id = :customerId")
                .param("customerId", customerId).query(Integer.class).single();
    }

    private void supervisorReview(String action, int draftId) throws Exception {
        mockMvc.perform(post("/api/supervisor/approval-drafts/{draftId}/{action}", draftId, action)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supervisorId":"SUPERVISOR_01"}
                                """))
                .andExpect(status().isOk());
    }
}
