package com.demo.creditcard.agent;

import java.util.stream.Stream;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class AgentTestConfiguration {

    static final String SUPERVISOR_ONLY_TOOL = "approveApprovalDraft";

    @Bean
    RecordingChatModel recordingChatModel() {
        return new RecordingChatModel();
    }

    @Bean
    ToolCallbackProvider fakeMcpTools() {
        return providerOf(Stream.concat(ToolAllowList.ALLOWED_TOOL_NAMES.stream(), Stream.of(SUPERVISOR_ONLY_TOOL)));
    }

    static ToolCallbackProvider providerOf(Stream<String> toolNames) {
        return new StaticToolCallbackProvider(toolNames.map(FakeToolCallback::new).toArray(ToolCallback[]::new));
    }
}
