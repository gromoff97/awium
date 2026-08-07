package io.github.gromoff97.assertility;

import org.awaitility.core.ConditionFactory;

import java.util.Objects;

final class Validation {
    private Validation() {
    }

    static ConditionFactory factory(ConditionFactory factory) {
        return Objects.requireNonNull(factory, "factory");
    }

    static <T> AwaitSources.Source<T> source(AwaitSources.Source<T> source) {
        return Objects.requireNonNull(source, "source");
    }

    static <T> T callback(T callback, String name) {
        return Objects.requireNonNull(callback, name);
    }

    static String predicateDescription(String description) {
        Objects.requireNonNull(description, "description");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        return description;
    }

    static String literalDescription(String description) {
        return Objects.requireNonNull(description, "description");
    }

    static String formattedDescription(String format, Object[] args) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(args, "args");
        return String.format(format, args);
    }
}
