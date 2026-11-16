package com.demo.creditcard.mcp.clerk;

import com.demo.creditcard.mcp.fraudcontrol.FraudVerdict;

public record CardListItem(
        String cardNumber,
        String last4,
        String holderName,
        FraudVerdict verdict,
        String verdictLabel,
        CardHandlingStatus status,
        String statusLabel,
        boolean actionable) {

    private static final int VISIBLE_DIGIT_COUNT = 4;

    public static CardListItem of(CardSnapshot card, FraudVerdict verdict, CardHandlingStatus status) {
        var cardNumber = card.cardNumber();
        return new CardListItem(cardNumber, cardNumber.substring(cardNumber.length() - VISIBLE_DIGIT_COUNT), card.holderName(),
                verdict, verdict.label(), status, status.label(), status.isActionable());
    }
}
