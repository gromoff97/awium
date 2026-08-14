package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.Locale;

import static java.util.Objects.requireNonNull;

public abstract class Condition<S, R> {

    protected Condition() {
    }

    public abstract Evaluation<R> evaluate(S actual) throws Exception;

    public abstract String description();

    public final ExplainedCondition<S, R> because(String explanation) {
        return new ExplainedCondition<>(this, explanation);
    }

    public final ExplainedCondition<S, R> because(String format, Object... arguments) {
        return new ExplainedCondition<>(this, formattedExplanation(format, arguments));
    }

    static String literalExplanation(String explanation) {
        return nonBlank(explanation, "explanation");
    }

    static String formattedExplanation(String format, Object[] arguments) {
        requireNonNull(format, "format must not be null");
        requireNonNull(arguments, "arguments must not be null");
        return String.format(Locale.ROOT, format, arguments);
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public record ExplainedCondition<S, R>(Condition<S, R> delegate, String explanation) {

        public ExplainedCondition {
            requireNonNull(delegate, "condition must not be null");
            explanation = literalExplanation(explanation);
        }
    }
}
