package com.demo.creditcard.agent;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

record FakeToolCallback(String name) implements ToolCallback {

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder().name(name).description(name).inputSchema("{\"type\":\"object\"}").build();
    }

    @Override
    public String call(String toolInput) {
        return "{}";
    }
}
