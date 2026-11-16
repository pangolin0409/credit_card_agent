package com.demo.creditcard.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class McpToolRegistrationTest {

    @Autowired
    private List<ToolCallbackProvider> toolCallbackProviders;

    @Test
    @DisplayName("MCP Server 只暴露 6 個工具，且不含主管放行功能")
    void shouldExposeExactlySixTools() {
        assertThat(toolNames()).containsExactlyInAnyOrder(
                "getApplicantProfile", "createApprovalDraft", "declineApplication",
                "getCardRiskProfile", "freezeCard", "escalateToRiskOfficer");
    }

    private List<String> toolNames() {
        return toolCallbackProviders.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .map(callback -> callback.getToolDefinition().name())
                .toList();
    }
}
