package com.demo.creditcard.agent.skill;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.demo.creditcard.agent.ToolAllowList;
import com.demo.creditcard.agent.skill.RoutingDecision.CallModel;
import com.demo.creditcard.agent.skill.RoutingDecision.ReplyDirectly;

@Component
public class SkillRouter {

    static final String UNKNOWN_COMMAND_REPLY = "查無此指令，請輸入 / 查看可用指令";
    static final String LIST_COMMAND_REPLY = "此指令會顯示清單，請從選單選取";
    static final String UNRESOLVED_TARGET_REPLY = "請從候選清單選取對象";
    static final String MISSING_QUESTION_REPLY = "請在指令後輸入問題";

    private static final String SECTION_SEPARATOR = "\n\n";

    private final SkillCatalog skillCatalog;

    public SkillRouter(SkillCatalog skillCatalog) {
        this.skillCatalog = skillCatalog;
    }

    public RoutingDecision route(String message) {
        return SlashCommand.parse(message)
                .map(this::routeCommand)
                .orElseGet(this::routeFreeChat);
    }

    private RoutingDecision routeCommand(SlashCommand command) {
        return skillCatalog.find(command.skillName())
                .map(skill -> routeSkill(skill, command.argumentText()))
                .orElseGet(() -> new ReplyDirectly(UNKNOWN_COMMAND_REPLY));
    }

    private RoutingDecision routeSkill(SkillDefinition skill, String argumentText) {
        if (skill.kind() == SkillKind.LIST) {
            return new ReplyDirectly(LIST_COMMAND_REPLY);
        }
        if (!skill.argument().accepts(argumentText)) {
            return new ReplyDirectly(skill.argument() == SkillArgument.TEXT ? MISSING_QUESTION_REPLY : UNRESOLVED_TARGET_REPLY);
        }
        var referencedSkills = skill.references().stream().map(reference -> skillCatalog.find(reference).orElseThrow());
        return new CallModel(composePrompt(Stream.concat(Stream.of(skill), referencedSkills).toList()), skill.toolNames());
    }

    private RoutingDecision routeFreeChat() {
        return new CallModel(composePrompt(skillCatalog.freeChatSkills()), ToolAllowList.ALLOWED_TOOL_NAMES);
    }

    private String composePrompt(List<SkillDefinition> skills) {
        return Stream.concat(Stream.of(skillCatalog.commonInstructions()), skills.stream().map(SkillDefinition::instructions))
                .reduce((first, second) -> first + SECTION_SEPARATOR + second)
                .orElseThrow();
    }
}
