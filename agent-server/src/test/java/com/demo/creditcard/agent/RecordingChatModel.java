package com.demo.creditcard.agent;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

class RecordingChatModel implements ChatModel {

    static final String STUB_REPLY = "stub reply";

    private Prompt lastPrompt;

    @Override
    public ChatResponse call(Prompt prompt) {
        lastPrompt = prompt;
        return new ChatResponse(List.of(new Generation(new AssistantMessage(STUB_REPLY))));
    }

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    Prompt lastPrompt() {
        return lastPrompt;
    }
}
