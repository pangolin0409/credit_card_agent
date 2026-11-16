package com.demo.creditcard.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.demo.creditcard.mcp.cardapplication.CardApplicationTools;
import com.demo.creditcard.mcp.fraudcontrol.FraudControlTools;

@Configuration
public class McpToolConfiguration {

    @Bean
    ToolCallbackProvider creditCardTools(CardApplicationTools cardApplicationTools, FraudControlTools fraudControlTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(cardApplicationTools, fraudControlTools)
                .build();
    }
}
