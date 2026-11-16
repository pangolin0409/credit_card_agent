package com.demo.creditcard.mcp.cardapplication;

public record ApplicantProfile(
        String customerId,
        String fullName,
        int monthlyIncome,
        int existingUnsecuredDebt,
        int creditScore,
        boolean isAmlBlacklisted) {
}
