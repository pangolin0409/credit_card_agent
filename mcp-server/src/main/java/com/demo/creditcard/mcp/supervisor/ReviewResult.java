package com.demo.creditcard.mcp.supervisor;

import com.demo.creditcard.mcp.cardapplication.DraftStatus;

public record ReviewResult(int draftId, DraftStatus status, String cardNumber) {
}
