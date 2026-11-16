package com.demo.creditcard.agent.skill;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public enum SkillArgument {
    CUSTOMER_ID("customerId", firstTokenMatches("^[A-Z]\\d{9}$")),
    CARD_NUMBER("cardNumber", firstTokenMatches("^\\d{16}$")),
    TEXT("text", argumentText -> !argumentText.isBlank()),
    NONE("none", argumentText -> true);

    private static final Pattern WHITESPACE = Pattern.compile("\\h+");

    private final String key;
    private final Predicate<String> rule;

    SkillArgument(String key, Predicate<String> rule) {
        this.key = key;
        this.rule = rule;
    }

    public String key() {
        return key;
    }

    public boolean accepts(String argumentText) {
        return rule.test(argumentText.strip());
    }

    public static SkillArgument fromKey(String key) {
        return Arrays.stream(values())
                .filter(argument -> argument.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown skill argument: " + key));
    }

    private static Predicate<String> firstTokenMatches(String regex) {
        var pattern = Pattern.compile(regex);
        return argumentText -> pattern.matcher(WHITESPACE.split(argumentText, 2)[0]).matches();
    }
}
