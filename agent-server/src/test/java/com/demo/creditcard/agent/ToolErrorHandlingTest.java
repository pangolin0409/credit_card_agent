package com.demo.creditcard.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(AgentTestConfiguration.class)
class ToolErrorHandlingTest {

    @Autowired
    private ToolExecutionExceptionProcessor toolExecutionExceptionProcessor;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("工具錯誤轉成 JSON 回給模型，讓 Gemini 能解析並告知經辦查無資料")
    void shouldConvertToolErrorToJson() {
        var exception = new ToolExecutionException(new FakeToolCallback("getCardRiskProfile").getToolDefinition(),
                new RuntimeException("Error calling tool: Card not found: 4000000000009999"));

        var toolResult = toolExecutionExceptionProcessor.process(exception);

        assertThat(jsonMapper.readTree(toolResult).get("error").asString()).contains("Card not found: 4000000000009999");
    }
}
