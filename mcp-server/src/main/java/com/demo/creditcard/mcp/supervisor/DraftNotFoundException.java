package com.demo.creditcard.mcp.supervisor;

public class DraftNotFoundException extends RuntimeException {

    public DraftNotFoundException(int draftId) {
        super("Approval draft not found: " + draftId);
    }
}
