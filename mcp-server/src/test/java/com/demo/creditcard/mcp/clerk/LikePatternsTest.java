package com.demo.creditcard.mcp.clerk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LikePatternsTest {

    @Test
    @DisplayName("包含比對時跳脫 LIKE 萬用字元與跳脫字元")
    void shouldEscapeWildcardsWhenBuildingContainsPattern() {
        assertThat(LikePatterns.contains("50%_\\")).isEqualTo("%50\\%\\_\\\\%");
    }

    @Test
    @DisplayName("結尾比對只在前面加萬用字元")
    void shouldPrefixWildcardWhenBuildingEndsWithPattern() {
        assertThat(LikePatterns.endsWith("0002")).isEqualTo("%0002");
    }
}
