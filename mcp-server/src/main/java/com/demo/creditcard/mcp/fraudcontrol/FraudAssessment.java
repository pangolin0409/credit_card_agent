package com.demo.creditcard.mcp.fraudcontrol;

public record FraudAssessment(FraudVerdict verdict, RiskMetrics metrics, String rationale) {
}
