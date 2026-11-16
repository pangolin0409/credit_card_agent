package com.demo.creditcard.mcp.clerk;

public enum ApplicationStatus {
    APPROVED("已核卡", false),
    PENDING_REVIEW("待主管放行", false),
    SUPERVISOR_REJECTED("主管退回", true),
    DECLINED("已婉拒", false),
    AWAITING_REVIEW("待初審", true);

    private final String label;
    private final boolean isActionable;

    ApplicationStatus(String label, boolean isActionable) {
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
