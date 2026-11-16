package com.demo.creditcard.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DatabaseSchemaIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("啟動時載入 demo 資料：13 位客戶、6 張卡、15 筆交易")
    void shouldSeedDemoData() {
        assertThat(count("customers")).isEqualTo(13);
        assertThat(count("credit_cards")).isEqualTo(6);
        assertThat(count("transactions")).isEqualTo(15);
        assertThat(count("approval_drafts")).isZero();
        assertThat(count("audit_logs")).isZero();
    }

    @Test
    @DisplayName("卡片狀態只允許 ACTIVE、TEMP_FROZEN、CLOSED")
    void shouldRejectUnknownCardStatus() {
        assertThatThrownBy(() -> jdbcClient
                .sql("UPDATE credit_cards SET status = 'LOST' WHERE card_number = '4000000000000006'")
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("稽核動作類型只允許 CLAUDE.md 定義的代碼")
    void shouldRejectUnknownAuditActionType() {
        assertThatThrownBy(() -> jdbcClient.sql("""
                        INSERT INTO audit_logs (target_id, action_type, reason)
                        VALUES ('4000000000000006', 'DELETE_CARD', 'invalid action')
                        """)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("草稿單預設狀態為 PENDING_REVIEW、建立者為 AI_AGENT_MCP")
    void shouldDefaultDraftStatusToPendingReview() {
        jdbcClient.sql("""
                        INSERT INTO approval_drafts (customer_id, card_type, credit_limit, assessment_summary)
                        VALUES ('A100000004', 'SIGNATURE', 150000, 'summary')
                        """)
                .update();

        var draft = jdbcClient.sql("SELECT status, created_by FROM approval_drafts WHERE customer_id = 'A100000004'")
                .query((resultSet, rowNumber) -> resultSet.getString("status") + "/" + resultSet.getString("created_by"))
                .single();

        assertThat(draft).isEqualTo("PENDING_REVIEW/AI_AGENT_MCP");
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }
}
