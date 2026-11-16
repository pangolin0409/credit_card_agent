package com.demo.creditcard.mcp.cardapplication;

import org.springframework.ai.tool.annotation.ToolParam;

public record ApprovalDraftRequest(
        @ToolParam(description = "Customer ID of the applicant") String customerId,
        @ToolParam(description = "Card tier recommended by the rule engine") CardTier cardTier,
        @ToolParam(description = "Credit limit in TWD, must not exceed the rule engine recommendation") int creditLimit,
        @ToolParam(description = "Assessment summary listing the matched rules and calculated figures") String assessmentSummary) {
}
