package com.demo.creditcard.agent.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class SkillFileParser {

    private static final String FRONTMATTER_DELIMITER = "---";
    private static final List<String> REQUIRED_KEYS = List.of("name", "description", "argument", "tools");

    public SkillDefinition parse(Resource resource) {
        var fileName = resource.getFilename();
        var lines = readContent(resource).lines().toList();
        var closingIndex = lines.isEmpty() || !lines.getFirst().equals(FRONTMATTER_DELIMITER)
                ? 0
                : lines.subList(1, lines.size()).indexOf(FRONTMATTER_DELIMITER) + 1;
        if (closingIndex == 0) {
            throw new IllegalStateException("Skill file %s must start with a --- frontmatter block".formatted(fileName));
        }
        var frontmatter = parseFrontmatter(lines.subList(1, closingIndex));
        REQUIRED_KEYS.stream()
                .filter(key -> !frontmatter.containsKey(key))
                .findFirst()
                .ifPresent(key -> {
                    throw new IllegalStateException("Skill file %s is missing '%s'".formatted(fileName, key));
                });
        var instructions = String.join("\n", lines.subList(closingIndex + 1, lines.size())).strip();
        return new SkillDefinition(
                frontmatter.get("name"),
                SkillKind.AGENT,
                frontmatter.get("description"),
                SkillArgument.fromKey(frontmatter.get("argument")),
                new LinkedHashSet<>(splitList(frontmatter.get("tools"))),
                splitList(frontmatter.getOrDefault("references", "")),
                Boolean.parseBoolean(frontmatter.getOrDefault("free-chat", "false")),
                instructions,
                null);
    }

    private static Map<String, String> parseFrontmatter(List<String> lines) {
        return lines.stream()
                .filter(line -> line.contains(":"))
                .collect(Collectors.toMap(
                        line -> line.substring(0, line.indexOf(':')).strip(),
                        line -> line.substring(line.indexOf(':') + 1).strip()));
    }

    private static List<String> splitList(String value) {
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    static String readContent(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read skill file " + resource.getFilename(), exception);
        }
    }
}
