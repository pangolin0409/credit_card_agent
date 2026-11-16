package com.demo.creditcard.agent;

import java.util.Map;

import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class ToolErrorConfiguration {

    @Bean
    ToolExecutionExceptionProcessor jsonToolExecutionExceptionProcessor(JsonMapper jsonMapper) {
        return exception -> jsonMapper.writeValueAsString(Map.of("error", String.valueOf(exception.getMessage())));
    }
}
