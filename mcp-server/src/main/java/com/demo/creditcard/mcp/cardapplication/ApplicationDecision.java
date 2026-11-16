package com.demo.creditcard.mcp.cardapplication;

public enum ApplicationDecision {
    DECLINE_AML_BLACKLIST,
    DECLINE_DEBT_BURDEN,
    DECLINE_BELOW_THRESHOLD,
    RECOMMEND_APPROVAL
}
