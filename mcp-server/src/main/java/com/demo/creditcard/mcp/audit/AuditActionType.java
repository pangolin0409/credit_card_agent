package com.demo.creditcard.mcp.audit;

public enum AuditActionType {
    APPROVAL_DRAFT,
    APPLICATION_DECLINE,
    TEMP_FREEZE,
    ESCALATION,
    GUARDRAIL_BLOCKED,
    DRAFT_APPROVED,
    DRAFT_REJECTED
}
