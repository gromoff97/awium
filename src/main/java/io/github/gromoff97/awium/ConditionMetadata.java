package io.github.gromoff97.awium;

import io.github.gromoff97.awium.ConditionResult.Reference;

import static java.util.Objects.requireNonNull;

record ConditionMetadata(String description, String explanation, Reference<?> reference) {

    ConditionMetadata {
        description = nonBlank(description, "description");
    }

    ConditionMetadata because(String reason) {
        return new ConditionMetadata(description, nonBlank(reason, "explanation"), reference);
    }

    ConditionMetadata prefixed(String prefix) {
        return new ConditionMetadata(prefix + description, explanation, reference);
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
