package com.demo.creditcard.mcp.shared;

public record ToolOutcome(boolean accepted, String message) {

    public static ToolOutcome accepted(String message) {
        return new ToolOutcome(true, message);
    }

    public static ToolOutcome rejected(String reason) {
        return new ToolOutcome(false, reason);
    }
}
