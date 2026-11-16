package com.demo.creditcard.mcp.fraudcontrol;

public enum FraudVerdict {
    ESCALATE_TO_RISK_OFFICER("超高金額紅線，需升級風控"),
    NORMAL_TRAVEL("出國旅遊正常，建議解除警報"),
    OVERSEAS_FRAUD("疑似境外盜刷"),
    CARD_TESTING("疑似小額測卡"),
    NO_ANOMALY("無異常");

    private final String label;

    FraudVerdict(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
