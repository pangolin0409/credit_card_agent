package com.demo.creditcard.mcp.clerk;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
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
class ClerkSearchIntegrationTest {

    private static final String APPLICANT_SEARCH = "/api/clerk/applicants/search";
    private static final String CARD_SEARCH = "/api/clerk/cards/search";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardApplicationTools cardApplicationTools;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("用姓氏搜尋申請人時，不包含同姓的既有持卡人")
    void shouldFindOnlyApplicantNotCardholderWhenSearchingSurname() throws Exception {
        search(APPLICANT_SEARCH, "林")
                .andExpect(jsonPath("$[*].customerId", contains("A100000002")))
                .andExpect(jsonPath("$[0].fullName").value("林淑芬"))
                .andExpect(jsonPath("$[0].statusLabel").value("待初審"));
    }

    @Test
    @DisplayName("客戶編號片段不分大小寫")
    void shouldMatchCustomerIdIgnoringCase() throws Exception {
        search(APPLICANT_SEARCH, "a1000000").andExpect(jsonPath("$", hasSize(7)));
    }

    @Test
    @DisplayName("用持卡人姓名搜尋卡片")
    void shouldFindCardByHolderName() throws Exception {
        search(CARD_SEARCH, "林")
                .andExpect(jsonPath("$[*].cardNumber", contains("4000000000000001")))
                .andExpect(jsonPath("$[0].holderName").value("林小旅"));
    }

    @Test
    @DisplayName("用卡號末碼搜尋卡片")
    void shouldFindCardByLastDigits() throws Exception {
        search(CARD_SEARCH, "0002").andExpect(jsonPath("$[*].cardNumber", contains("4000000000000002")));
    }

    @Test
    @DisplayName("卡片搜尋也包含無異常的卡片")
    void shouldIncludeNormalCardInCardSearch() throws Exception {
        search(CARD_SEARCH, "0006")
                .andExpect(jsonPath("$[*].cardNumber", contains("4000000000000006")))
                .andExpect(jsonPath("$[0].verdict").value("NO_ANOMALY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("搜尋字串空白時回傳空陣列")
    void shouldReturnEmptyWhenKeywordBlank(String keyword) throws Exception {
        search(APPLICANT_SEARCH, keyword).andExpect(jsonPath("$", hasSize(0)));
        search(CARD_SEARCH, keyword).andExpect(jsonPath("$", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "'", "\\"})
    @DisplayName("萬用字元與引號以字面比對，不報錯")
    void shouldTreatWildcardsLiterally(String keyword) throws Exception {
        search(APPLICANT_SEARCH, keyword).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        search(CARD_SEARCH, keyword).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("主管核准發卡後，申請人搜尋仍找得到並顯示已核卡")
    void shouldStillFindApplicantAfterApproval() throws Exception {
        cardApplicationTools.createApprovalDraft(new ApprovalDraftRequest("A100000004", CardTier.SIGNATURE, 150_000, "符合御璽卡"));
        var draftId = jdbcClient.sql("SELECT draft_id FROM approval_drafts WHERE customer_id = 'A100000004'").query(Integer.class).single();
        mockMvc.perform(post("/api/supervisor/approval-drafts/{draftId}/approve", draftId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"supervisorId":"SUPERVISOR_01"}
                        """)).andExpect(status().isOk());

        search(APPLICANT_SEARCH, "張雅婷")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
        search(CARD_SEARCH, "張雅婷")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].holderName").value("張雅婷"));
    }

    @Test
    @Sql("/clerk/eleven-applicants.sql")
    @DisplayName("搜尋結果最多 10 筆")
    void shouldLimitResultsToTen() throws Exception {
        search(APPLICANT_SEARCH, "測").andExpect(jsonPath("$", hasSize(10)));
    }

    private ResultActions search(String path, String keyword) throws Exception {
        return mockMvc.perform(get(path).param("q", keyword)).andExpect(status().isOk());
    }
}
