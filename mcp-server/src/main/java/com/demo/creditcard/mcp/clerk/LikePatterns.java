package com.demo.creditcard.mcp.clerk;

public final class LikePatterns {

    private static final String ESCAPE = "\\";
    private static final String WILDCARD = "%";

    private LikePatterns() {
    }

    public static String contains(String keyword) {
        return WILDCARD + escape(keyword) + WILDCARD;
    }

    public static String endsWith(String keyword) {
        return WILDCARD + escape(keyword);
    }

    private static String escape(String keyword) {
        return keyword.replace(ESCAPE, ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
    }
}
