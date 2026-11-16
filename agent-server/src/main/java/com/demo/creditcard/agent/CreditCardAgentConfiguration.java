package com.demo.creditcard.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class CreditCardAgentConfiguration {

    @Bean
    ChatClient creditCardAgentChatClient(ChatClient.Builder builder, ChatMemory chatMemory,
            @Value("classpath:skills/credit_card_sop.md") Resource creditCardSop) {
        return builder
                .defaultSystem(creditCardSop)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
