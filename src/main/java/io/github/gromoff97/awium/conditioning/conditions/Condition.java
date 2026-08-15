package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.Locale;
import java.util.Optional;

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
        if (requireNonNull(explanation, "explanation must not be null").isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
        return explanation;
    }

    static String formattedExplanation(String format, Object[] arguments) {
        requireNonNull(format, "format must not be null");
        requireNonNull(arguments, "arguments must not be null");
        return String.format(Locale.ROOT, format, arguments);
    }

    public record ExplainedCondition<S, R>(Condition<S, R> delegate, String explanation) {

        public ExplainedCondition {
            requireNonNull(delegate, "condition must not be null");
            explanation = literalExplanation(explanation);
        }
    }

    public record PreservingCondition<S>(Condition<S, S> delegate) {

        public PreservingCondition {
            requireNonNull(delegate, "condition must not be null");
        }

        public ExplainedCondition<S> because(String explanation) {
            return new ExplainedCondition<>(this, explanation);
        }

        public ExplainedCondition<S> because(String format, Object... arguments) {
            return new ExplainedCondition<>(this, formattedExplanation(format, arguments));
        }

        public record ExplainedCondition<S>(PreservingCondition<S> delegate, String explanation) {

            public ExplainedCondition {
                requireNonNull(delegate, "condition must not be null");
                explanation = literalExplanation(explanation);
            }
        }
    }

    public record PresentCondition(Condition<Optional<?>, Object> delegate) {

        public PresentCondition {
            requireNonNull(delegate, "condition must not be null");
        }

        public ExplainedCondition because(String explanation) {
            return new ExplainedCondition(this, explanation);
        }

        public ExplainedCondition because(String format, Object... arguments) {
            return new ExplainedCondition(this, formattedExplanation(format, arguments));
        }

        public record ExplainedCondition(PresentCondition delegate, String explanation) {

            public ExplainedCondition {
                requireNonNull(delegate, "condition must not be null");
                explanation = literalExplanation(explanation);
            }
        }
    }
}
