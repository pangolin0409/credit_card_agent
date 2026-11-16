package com.demo.creditcard.agent.skill;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record SlashCommand(String skillName, String argumentText) {

    private static final Set<Character> SLASHES = Set.of('/', '／');
    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\h+");

    public static Optional<SlashCommand> parse(String message) {
        var stripped = message.strip();
        if (stripped.isEmpty() || !SLASHES.contains(stripped.charAt(0))) {
            return Optional.empty();
        }
        var parts = NAME_SEPARATOR.split(stripped.substring(1), 2);
        var argumentText = parts.length > 1 ? parts[1].strip() : "";
        return Optional.of(new SlashCommand(parts[0], argumentText));
    }
}
