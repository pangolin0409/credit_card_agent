package com.demo.creditcard.agent.skill;

import java.util.Set;

public sealed interface RoutingDecision {

    record ReplyDirectly(String reply) implements RoutingDecision {
    }

    record CallModel(String systemPrompt, Set<String> toolNames) implements RoutingDecision {
    }
}
