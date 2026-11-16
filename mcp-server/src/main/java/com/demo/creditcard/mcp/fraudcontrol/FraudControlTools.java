package com.demo.creditcard.mcp.fraudcontrol;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.demo.creditcard.mcp.shared.ToolOutcome;

@Component
public class FraudControlTools {

    private final FraudControlService fraudControlService;

    public FraudControlTools(FraudControlService fraudControlService) {
        this.fraudControlService = fraudControlService;
    }

    @Tool(name = "getCardRiskProfile", description = """
            Read a credit card's status, the cardholder's travel notice, transactions within 24 hours of the latest
            transaction, and the rule engine assessment: max single amount, foreign online amount within one hour,
            micro transaction count within ten minutes, and the verdict. Always call this before acting on a card.""")
    public CardRiskProfile getCardRiskProfile(@ToolParam(description = "Credit card number") String cardNumber) {
        return fraudControlService.getCardRiskProfile(cardNumber);
    }

    @Tool(name = "freezeCard", description = """
            Set a card to TEMP_FROZEN to stop further fraud losses. Rejected when the card is not ACTIVE or when any
            recent single transaction exceeds 500,000 TWD; in that case use escalateToRiskOfficer instead.""")
    public ToolOutcome freezeCard(
            @ToolParam(description = "Credit card number") String cardNumber,
            @ToolParam(description = "Freeze reason listing the matched rule and figures") String freezeReason) {
        return fraudControlService.freezeCard(cardNumber, freezeReason);
    }

    @Tool(name = "escalateToRiskOfficer", description = """
            Hand a case over to the senior risk officer for manual handling without changing the card status.
            Required when any single transaction exceeds 500,000 TWD.""")
    public ToolOutcome escalateToRiskOfficer(
            @ToolParam(description = "Credit card number") String cardNumber,
            @ToolParam(description = "Case summary with the organised transaction details") String caseSummary) {
        return fraudControlService.escalateToRiskOfficer(cardNumber, caseSummary);
    }
}
