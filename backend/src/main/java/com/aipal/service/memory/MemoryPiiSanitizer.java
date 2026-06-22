package com.aipal.service.memory;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class MemoryPiiSanitizer {

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"), "[EMAIL_REDACTED]"),
            new Rule(Pattern.compile("(?<!\\d)(?:1[3-9]\\d{9})(?!\\d)"), "[PHONE_REDACTED]"),
            new Rule(Pattern.compile("(?<![0-9Xx])\\d{17}[0-9Xx](?![0-9Xx])"), "[ID_REDACTED]"),
            new Rule(Pattern.compile("(?i)\\b(?:sk|rk|pk)_[a-z0-9]{16,}\\b"), "[API_KEY_REDACTED]"),
            new Rule(Pattern.compile("(?i)bearer\\s+[a-z0-9._-]{16,}"), "Bearer [TOKEN_REDACTED]")
    );

    public SanitizedContent sanitize(String value) {
        if (value == null || value.isBlank()) return new SanitizedContent("", false);
        String sanitized = value;
        for (Rule rule : RULES) {
            sanitized = rule.pattern().matcher(sanitized).replaceAll(rule.replacement());
        }
        return new SanitizedContent(sanitized, !sanitized.equals(value));
    }

    private record Rule(Pattern pattern, String replacement) {
    }

    public record SanitizedContent(String content, boolean redacted) {
    }
}
