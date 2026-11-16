package com.demo.creditcard.mcp.fraudcontrol;

public enum FraudVerdict {
    ESCALATE_TO_RISK_OFFICER,
    NORMAL_TRAVEL,
    OVERSEAS_FRAUD,
    CARD_TESTING,
    NO_ANOMALY
}
