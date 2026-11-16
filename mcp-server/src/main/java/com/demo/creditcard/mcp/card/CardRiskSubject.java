package com.demo.creditcard.mcp.card;

public record CardRiskSubject(
        String cardNumber,
        String customerId,
        String fullName,
        CardStatus status,
        boolean hasTravelNotice,
        String travelCountry) {
}
