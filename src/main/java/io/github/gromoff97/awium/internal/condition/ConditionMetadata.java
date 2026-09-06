package io.github.gromoff97.awium.internal.condition;

import io.github.gromoff97.awium.results.AwaitAttempt.Reference;

import static java.util.Objects.requireNonNull;

public record ConditionMetadata(String description, String explanation, Reference<?> reference) {

    public ConditionMetadata {
        description = nonBlank(description, "description");
    }

    public ConditionMetadata because(String reason) {
        return new ConditionMetadata(description, nonBlank(reason, "explanation"), reference);
    }

    public ConditionMetadata prefixed(String prefix) {
        return new ConditionMetadata(prefix + description, explanation, reference);
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
