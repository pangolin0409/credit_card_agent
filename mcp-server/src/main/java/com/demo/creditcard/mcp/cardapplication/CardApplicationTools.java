package com.demo.creditcard.mcp.cardapplication;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.demo.creditcard.mcp.shared.ToolOutcome;

@Component
public class CardApplicationTools {

    private final CardApplicationService cardApplicationService;

    public CardApplicationTools(CardApplicationService cardApplicationService) {
        this.cardApplicationService = cardApplicationService;
    }

    @Tool(name = "getApplicantProfile", description = """
            Read a credit card applicant's financial and credit profile together with the rule engine assessment:
            remaining debt capacity (monthly income x 22 minus existing unsecured debt), decision,
            recommended card tier and recommended credit limit. Always call this before deciding on an application.""")
    public ApplicantProfileResult getApplicantProfile(@ToolParam(description = "Customer ID of the applicant") String customerId) {
        return cardApplicationService.getApplicantProfile(customerId);
    }

    @Tool(name = "createApprovalDraft", description = """
            Create a preliminary card approval draft that a human supervisor must approve. This does NOT issue a card.
            Rejected when the applicant is blacklisted, the debt burden rule fails, the card tier differs from the
            rule engine recommendation, the credit limit exceeds the recommendation, or a pending draft already exists.""")
    public ToolOutcome createApprovalDraft(ApprovalDraftRequest request) {
        return cardApplicationService.createApprovalDraft(request);
    }

    @Tool(name = "declineApplication", description = """
            Record the decline reason for an application that is rejected by the rules
            (AML blacklist, debt burden too high, or below card thresholds).""")
    public ToolOutcome declineApplication(
            @ToolParam(description = "Customer ID of the applicant") String customerId,
            @ToolParam(description = "Decline reason listing the matched rule and figures") String reason) {
        return cardApplicationService.declineApplication(customerId, reason);
    }
}
