package com.demo.creditcard.mcp.fraudcontrol;

public record RiskMetrics(
        int maxSingleAmount,
        int foreignOnlineAmountWithinOneHour,
        int microTransactionCountWithinTenMinutes) {
}
