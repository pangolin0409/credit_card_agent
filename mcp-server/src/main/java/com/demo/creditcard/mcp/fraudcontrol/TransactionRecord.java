package com.demo.creditcard.mcp.fraudcontrol;

import java.time.LocalDateTime;

public record TransactionRecord(
        String transactionId,
        LocalDateTime transactionTime,
        int amount,
        String merchantName,
        String countryCode,
        boolean isOnline) {
}
