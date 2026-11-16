package com.demo.creditcard.agent.skill;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import com.demo.creditcard.agent.ToolAllowList;

public class SkillCatalog {

    private final String commonInstructions;
    private final List<SkillDefinition> skills;

    public SkillCatalog(String commonInstructions, List<SkillDefinition> skills) {
        this.commonInstructions = commonInstructions;
        this.skills = List.copyOf(skills);
        rejectDuplicateNames();
        skills.forEach(this::rejectToolsOutsideAllowList);
        skills.forEach(this::rejectUnknownReferences);
    }

    public Optional<SkillDefinition> find(String name) {
        return skills.stream().filter(skill -> skill.name().equals(name)).findFirst();
    }

    public List<SkillDefinition> all() {
        return skills;
    }

    public List<SkillDefinition> freeChatSkills() {
        return skills.stream().filter(SkillDefinition::isIncludedInFreeChat).toList();
    }

    public String commonInstructions() {
        return commonInstructions;
    }

    private void rejectDuplicateNames() {
        var seenNames = new HashSet<String>();
        skills.stream()
                .map(SkillDefinition::name)
                .filter(name -> !seenNames.add(name))
                .findFirst()
                .ifPresent(name -> {
                    throw new IllegalStateException("Duplicate skill name: " + name);
                });
    }

    private void rejectToolsOutsideAllowList(SkillDefinition skill) {
        skill.toolNames().stream()
                .filter(toolName -> !ToolAllowList.ALLOWED_TOOL_NAMES.contains(toolName))
                .findFirst()
                .ifPresent(toolName -> {
                    throw new IllegalStateException("Skill %s uses tool outside allow list: %s".formatted(skill.name(), toolName));
                });
    }

    private void rejectUnknownReferences(SkillDefinition skill) {
        skill.references().stream()
                .filter(reference -> find(reference).filter(target -> target.kind() == SkillKind.AGENT).isEmpty())
                .findFirst()
                .ifPresent(reference -> {
                    throw new IllegalStateException("Skill %s references unknown skill: %s".formatted(skill.name(), reference));
                });
    }
}
