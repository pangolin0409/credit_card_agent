package com.demo.creditcard.agent;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import com.demo.creditcard.agent.skill.RoutingDecision.CallModel;
import com.demo.creditcard.agent.skill.RoutingDecision.ReplyDirectly;
import com.demo.creditcard.agent.skill.SkillRouter;

@Service
public class CreditCardAgentService {

    private final ChatClient creditCardAgentChatClient;
    private final List<ToolCallbackProvider> toolCallbackProviders;
    private final ToolAllowList toolAllowList;
    private final SkillRouter skillRouter;

    public CreditCardAgentService(ChatClient creditCardAgentChatClient, List<ToolCallbackProvider> toolCallbackProviders,
            ToolAllowList toolAllowList, SkillRouter skillRouter) {
        this.creditCardAgentChatClient = creditCardAgentChatClient;
        this.toolCallbackProviders = toolCallbackProviders;
        this.toolAllowList = toolAllowList;
        this.skillRouter = skillRouter;
    }

    public String chat(String conversationId, String message) {
        return switch (skillRouter.route(message)) {
            case ReplyDirectly directReply -> directReply.reply();
            case CallModel modelCall -> callModel(conversationId, message, modelCall);
        };
    }

    private String callModel(String conversationId, String message, CallModel modelCall) {
        return creditCardAgentChatClient.prompt()
                .system(modelCall.systemPrompt())
                .user(message)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolCallbacks(toolAllowList.filter(toolCallbackProviders, modelCall.toolNames()))
                .call()
                .content();
    }
}
