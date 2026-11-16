package com.demo.creditcard.agent.skill;

import java.util.List;
import java.util.Set;

public record SkillDefinition(
        String name,
        SkillKind kind,
        String description,
        SkillArgument argument,
        Set<String> toolNames,
        List<String> references,
        boolean isIncludedInFreeChat,
        String instructions,
        String source) {

    public static SkillDefinition list(String name, String description, String source) {
        return new SkillDefinition(name, SkillKind.LIST, description, SkillArgument.NONE, Set.of(), List.of(), false, "", source);
    }
}
