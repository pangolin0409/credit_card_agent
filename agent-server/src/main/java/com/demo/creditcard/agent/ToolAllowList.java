package com.demo.creditcard.agent;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

@Component
public class ToolAllowList {

    public static final Set<String> ALLOWED_TOOL_NAMES = Set.of(
            "getApplicantProfile", "createApprovalDraft", "declineApplication",
            "getCardRiskProfile", "freezeCard", "escalateToRiskOfficer");

    public List<ToolCallback> filter(List<ToolCallbackProvider> providers) {
        return filter(providers, ALLOWED_TOOL_NAMES);
    }

    public List<ToolCallback> filter(List<ToolCallbackProvider> providers, Set<String> requestedToolNames) {
        return providers.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .filter(callback -> isAllowedAndRequested(callback.getToolDefinition().name(), requestedToolNames))
                .toList();
    }

    private static boolean isAllowedAndRequested(String toolName, Set<String> requestedToolNames) {
        return ALLOWED_TOOL_NAMES.contains(toolName) && requestedToolNames.contains(toolName);
    }
}
