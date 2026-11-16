package com.demo.creditcard.mcp.clerk;

public enum CardHandlingStatus {
    FROZEN("已凍結", false),
    ESCALATED("已升級風控", false),
    AWAITING_ACTION("待處理", true);

    private final String label;
    private final boolean isActionable;

    CardHandlingStatus(String label, boolean isActionable) {
        this.label = label;
        this.isActionable = isActionable;
    }

    public String label() {
        return label;
    }

    public boolean isActionable() {
        return isActionable;
    }
}
