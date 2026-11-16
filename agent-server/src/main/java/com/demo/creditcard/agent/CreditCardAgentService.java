package com.demo.creditcard.agent;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

@Service
public class CreditCardAgentService {

    private final ChatClient creditCardAgentChatClient;
    private final List<ToolCallbackProvider> toolCallbackProviders;
    private final ToolAllowList toolAllowList;

    public CreditCardAgentService(ChatClient creditCardAgentChatClient, List<ToolCallbackProvider> toolCallbackProviders,
            ToolAllowList toolAllowList) {
        this.creditCardAgentChatClient = creditCardAgentChatClient;
        this.toolCallbackProviders = toolCallbackProviders;
        this.toolAllowList = toolAllowList;
    }

    public String chat(String message) {
        return creditCardAgentChatClient.prompt()
                .user(message)
                .toolCallbacks(toolAllowList.filter(toolCallbackProviders))
                .call()
                .content();
    }
}
