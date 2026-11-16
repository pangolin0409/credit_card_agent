package com.demo.creditcard.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolAllowListTest {

    @Test
    @DisplayName("白名單過濾後只保留 6 個工具，主管放行工具被排除")
    void shouldKeepOnlyAllowListedTools() {
        var provider = AgentTestConfiguration.providerOf(
                Stream.concat(ToolAllowList.ALLOWED_TOOL_NAMES.stream(), Stream.of(AgentTestConfiguration.SUPERVISOR_ONLY_TOOL)));

        var names = new ToolAllowList().filter(List.of(provider)).stream()
                .map(callback -> callback.getToolDefinition().name())
                .toList();

        assertThat(names).containsExactlyInAnyOrderElementsOf(ToolAllowList.ALLOWED_TOOL_NAMES)
                .doesNotContain(AgentTestConfiguration.SUPERVISOR_ONLY_TOOL);
    }

    @Test
    @DisplayName("白名單即為 MCP Server 的 6 個工具")
    void shouldAllowExactlySixTools() {
        assertThat(ToolAllowList.ALLOWED_TOOL_NAMES).containsExactlyInAnyOrder(
                "getApplicantProfile", "createApprovalDraft", "declineApplication",
                "getCardRiskProfile", "freezeCard", "escalateToRiskOfficer");
    }

    @Test
    @DisplayName("依 skill 要求的工具篩選，只回傳白名單內的交集")
    void shouldKeepOnlyRequestedToolsWithinAllowList() {
        assertThat(toolNamesFor(Set.of("freezeCard"))).containsExactly("freezeCard");
    }

    @Test
    @DisplayName("skill 要求白名單以外的工具時一律排除")
    void shouldExcludeRequestedToolOutsideAllowList() {
        assertThat(toolNamesFor(Set.of(AgentTestConfiguration.SUPERVISOR_ONLY_TOOL))).isEmpty();
    }

    private static List<String> toolNamesFor(Set<String> requestedToolNames) {
        var provider = AgentTestConfiguration.providerOf(
                Stream.concat(ToolAllowList.ALLOWED_TOOL_NAMES.stream(), Stream.of(AgentTestConfiguration.SUPERVISOR_ONLY_TOOL)));
        return new ToolAllowList().filter(List.of(provider), requestedToolNames).stream()
                .map(callback -> callback.getToolDefinition().name())
                .toList();
    }
}
