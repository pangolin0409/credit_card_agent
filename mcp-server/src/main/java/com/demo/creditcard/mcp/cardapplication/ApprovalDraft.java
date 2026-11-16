package com.demo.creditcard.mcp.cardapplication;

import java.time.LocalDateTime;

public record ApprovalDraft(
        int draftId,
        String customerId,
        String fullName,
        CardTier cardType,
        int creditLimit,
        String assessmentSummary,
        DraftStatus status,
        LocalDateTime createdAt,
        String reviewedBy,
        LocalDateTime reviewedAt) {
}
