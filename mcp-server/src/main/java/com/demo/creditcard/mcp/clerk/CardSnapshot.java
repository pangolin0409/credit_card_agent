package com.demo.creditcard.mcp.clerk;

import com.demo.creditcard.mcp.card.CardStatus;

public record CardSnapshot(String cardNumber, String holderName, CardStatus status, boolean hasEscalation) {
}
