package com.demo.creditcard.mcp.fraudcontrol;

import java.util.List;

import com.demo.creditcard.mcp.card.CardRiskSubject;

public record CardRiskProfile(CardRiskSubject card, List<TransactionRecord> recentTransactions, FraudAssessment assessment) {
}
