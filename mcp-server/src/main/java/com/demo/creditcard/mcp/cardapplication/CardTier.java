package com.demo.creditcard.mcp.cardapplication;

public enum CardTier {
    INFINITE(500_000),
    SIGNATURE(150_000);

    private final int maximumCreditLimit;

    CardTier(int maximumCreditLimit) {
        this.maximumCreditLimit = maximumCreditLimit;
    }

    public int maximumCreditLimit() {
        return maximumCreditLimit;
    }
}
