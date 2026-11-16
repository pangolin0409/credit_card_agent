package com.demo.creditcard.mcp.cardapplication;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalDraftRepository {

    private static final String SELECT_DRAFT = """
            SELECT d.draft_id, d.customer_id, c.full_name, d.card_type, d.credit_limit, d.assessment_summary,
                   d.status, d.created_at, d.reviewed_by, d.reviewed_at
              FROM approval_drafts d
              JOIN customers c ON c.customer_id = d.customer_id
            """;

    private final JdbcClient jdbcClient;

    public ApprovalDraftRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int insert(ApprovalDraftRequest request) {
        return jdbcClient.sql("""
                        INSERT INTO approval_drafts (customer_id, card_type, credit_limit, assessment_summary)
                        VALUES (:customerId, :cardType, :creditLimit, :assessmentSummary)
                        RETURNING draft_id
                        """)
                .param("customerId", request.customerId())
                .param("cardType", request.cardTier().name())
                .param("creditLimit", request.creditLimit())
                .param("assessmentSummary", request.assessmentSummary())
                .query(Integer.class)
                .single();
    }

    public boolean existsPendingByCustomerId(String customerId) {
        return jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM approval_drafts
                             WHERE customer_id = :customerId AND status = 'PENDING_REVIEW')
                        """)
                .param("customerId", customerId)
                .query(Boolean.class)
                .single();
    }

    public List<ApprovalDraft> findByStatus(DraftStatus status) {
        return jdbcClient.sql(SELECT_DRAFT + " WHERE d.status = :status ORDER BY d.draft_id")
                .param("status", status.name())
                .query(ApprovalDraftRepository::mapDraft)
                .list();
    }

    public Optional<ApprovalDraft> findById(int draftId) {
        return jdbcClient.sql(SELECT_DRAFT + " WHERE d.draft_id = :draftId")
                .param("draftId", draftId)
                .query(ApprovalDraftRepository::mapDraft)
                .optional();
    }

    public void markReviewed(int draftId, DraftStatus status, String reviewedBy) {
        jdbcClient.sql("""
                        UPDATE approval_drafts
                           SET status = :status,
                               reviewed_by = :reviewedBy,
                               reviewed_at = CURRENT_TIMESTAMP
                         WHERE draft_id = :draftId
                        """)
                .param("status", status.name())
                .param("reviewedBy", reviewedBy)
                .param("draftId", draftId)
                .update();
    }

    private static ApprovalDraft mapDraft(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ApprovalDraft(
                resultSet.getInt("draft_id"),
                resultSet.getString("customer_id"),
                resultSet.getString("full_name"),
                CardTier.valueOf(resultSet.getString("card_type")),
                resultSet.getInt("credit_limit"),
                resultSet.getString("assessment_summary"),
                DraftStatus.valueOf(resultSet.getString("status")),
                toLocalDateTime(resultSet.getTimestamp("created_at")),
                resultSet.getString("reviewed_by"),
                toLocalDateTime(resultSet.getTimestamp("reviewed_at")));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
