package com.demo.creditcard.agent;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(String conversationId, @NotBlank String message) {

    String resolvedConversationId() {
        return conversationId == null || conversationId.isBlank() ? UUID.randomUUID().toString() : conversationId;
    }
}
