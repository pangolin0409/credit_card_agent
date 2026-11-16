package com.demo.creditcard.agent.skill;

public record SkillSummary(String name, String kind, String description, String argument, String source) {

    public static SkillSummary from(SkillDefinition skill) {
        var argument = skill.argument() == SkillArgument.NONE ? null : skill.argument().key();
        return new SkillSummary(skill.name(), skill.kind().name(), skill.description(), argument, skill.source());
    }
}
