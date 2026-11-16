package com.demo.creditcard.mcp.clerk;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.demo.creditcard.mcp.card.CardStatus;
import com.demo.creditcard.mcp.cardapplication.DraftStatus;

@Repository
public class ClerkQueryRepository {

    private final JdbcClient jdbcClient;

    public ClerkQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<ApplicantSnapshot> findApplicants() {
        return jdbcClient.sql("""
                        SELECT c.customer_id,
                               c.full_name,
                               (SELECT d.status
                                  FROM approval_drafts d
                                 WHERE d.customer_id = c.customer_id
                                 ORDER BY d.draft_id DESC
                                 LIMIT 1) AS latest_draft_status,
                               EXISTS (SELECT 1
                                         FROM audit_logs a
                                        WHERE a.target_id = c.customer_id
                                          AND a.action_type = 'APPLICATION_DECLINE') AS has_decline
                          FROM customers c
                         WHERE NOT EXISTS (SELECT 1 FROM credit_cards cc WHERE cc.customer_id = c.customer_id)
                            OR EXISTS (SELECT 1 FROM approval_drafts d WHERE d.customer_id = c.customer_id)
                         ORDER BY c.customer_id
                        """)
                .query(ClerkQueryRepository::mapApplicant)
                .list();
    }

    public List<CardSnapshot> findCards() {
        return jdbcClient.sql("""
                        SELECT cc.card_number,
                               c.full_name,
                               cc.status,
                               EXISTS (SELECT 1
                                         FROM audit_logs a
                                        WHERE a.target_id = cc.card_number
                                          AND a.action_type = 'ESCALATION') AS has_escalation
                          FROM credit_cards cc
                          JOIN customers c ON c.customer_id = cc.customer_id
                         ORDER BY cc.card_number
                        """)
                .query(ClerkQueryRepository::mapCard)
                .list();
    }

    public List<ApplicantSnapshot> searchApplicants(String containsPattern, int limit) {
        return jdbcClient.sql("""
                        SELECT c.customer_id,
                               c.full_name,
                               (SELECT d.status
                                  FROM approval_drafts d
                                 WHERE d.customer_id = c.customer_id
                                 ORDER BY d.draft_id DESC
                                 LIMIT 1) AS latest_draft_status,
                               EXISTS (SELECT 1
                                         FROM audit_logs a
                                        WHERE a.target_id = c.customer_id
                                          AND a.action_type = 'APPLICATION_DECLINE') AS has_decline
                          FROM customers c
                         WHERE (NOT EXISTS (SELECT 1 FROM credit_cards cc WHERE cc.customer_id = c.customer_id)
                                OR EXISTS (SELECT 1 FROM approval_drafts d WHERE d.customer_id = c.customer_id))
                           AND (c.full_name LIKE :containsPattern ESCAPE '\\'
                                OR UPPER(c.customer_id) LIKE UPPER(:containsPattern) ESCAPE '\\')
                         ORDER BY c.customer_id
                         LIMIT :limit
                        """)
                .param("containsPattern", containsPattern)
                .param("limit", limit)
                .query(ClerkQueryRepository::mapApplicant)
                .list();
    }

    public List<CardSnapshot> searchCards(String containsPattern, String suffixPattern, int limit) {
        return jdbcClient.sql("""
                        SELECT cc.card_number,
                               c.full_name,
                               cc.status,
                               EXISTS (SELECT 1
                                         FROM audit_logs a
                                        WHERE a.target_id = cc.card_number
                                          AND a.action_type = 'ESCALATION') AS has_escalation
                          FROM credit_cards cc
                          JOIN customers c ON c.customer_id = cc.customer_id
                         WHERE c.full_name LIKE :containsPattern ESCAPE '\\'
                            OR cc.card_number LIKE :suffixPattern ESCAPE '\\'
                         ORDER BY cc.card_number
                         LIMIT :limit
                        """)
                .param("containsPattern", containsPattern)
                .param("suffixPattern", suffixPattern)
                .param("limit", limit)
                .query(ClerkQueryRepository::mapCard)
                .list();
    }

    private static ApplicantSnapshot mapApplicant(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ApplicantSnapshot(
                resultSet.getString("customer_id"),
                resultSet.getString("full_name"),
                Optional.ofNullable(resultSet.getString("latest_draft_status")).map(DraftStatus::valueOf),
                resultSet.getBoolean("has_decline"));
    }

    private static CardSnapshot mapCard(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CardSnapshot(
                resultSet.getString("card_number"),
                resultSet.getString("full_name"),
                CardStatus.valueOf(resultSet.getString("status")),
                resultSet.getBoolean("has_escalation"));
    }
}
