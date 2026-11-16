package com.demo.creditcard.mcp.audit;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

    public static final String AI_OPERATOR = "AI_AGENT_MCP";

    private final JdbcClient jdbcClient;

    public AuditLogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void append(String targetId, AuditActionType actionType, String reason, String operator) {
        jdbcClient.sql("""
                        INSERT INTO audit_logs (target_id, action_type, reason, operator)
                        VALUES (:targetId, :actionType, :reason, :operator)
                        """)
                .param("targetId", targetId)
                .param("actionType", actionType.name())
                .param("reason", reason)
                .param("operator", operator)
                .update();
    }
}
