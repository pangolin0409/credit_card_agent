package com.demo.creditcard.agent;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final CreditCardAgentService creditCardAgentService;

    public AgentController(CreditCardAgentService creditCardAgentService) {
        this.creditCardAgentService = creditCardAgentService;
    }

    @PostMapping("/chat")
    public ChatReply chat(@Valid @RequestBody ChatRequest request) {
        return new ChatReply(creditCardAgentService.chat(request.resolvedConversationId(), request.message()));
    }
}
