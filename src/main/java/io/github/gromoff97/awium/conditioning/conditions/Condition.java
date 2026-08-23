package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.CheckedConsumer;
import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.Locale;
import java.util.Optional;

import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static java.util.Objects.requireNonNull;

public abstract class Condition<S, R> {

    protected Condition() {
    }

    public abstract Evaluation<R> evaluate(S actual) throws Exception;

    public abstract String description();

    public static <S, R> Condition<S, R> condition(String description,
            CheckedFunction<? super S, Evaluation<R>> evaluation) {
        if (requireNonNull(description, "description must not be null").isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        requireNonNull(evaluation, "evaluation must not be null");
        return new Condition<>() {
            @Override
            public Evaluation<R> evaluate(S actual) throws Exception {
                return evaluation.apply(actual);
            }

            @Override
            public String description() {
                return description;
            }
        };
    }

    public static <S> PreservingCondition<S> asserted(CheckedConsumer<? super S> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return new PreservingCondition<>(evaluated("value satisfies assertion",
                "value did not satisfy assertion", actual -> {
            assertion.accept(actual);
            return actual;
        }));
    }

    public static <S, R> Condition<S, R> yields(CheckedFunction<? super S, ? extends R> callback) {
        requireNonNull(callback, "callback must not be null");
        return evaluated("callback yields a result", "callback did not yield a result", callback);
    }

    private static <S, R> Condition<S, R> evaluated(String description, String mismatch,
            CheckedFunction<? super S, ? extends R> function) {
        return condition(description, actual -> {
            try {
                return satisfied(function.apply(actual));
            } catch (AssertionError error) {
                return assertionUnsatisfied(mismatch, error);
            }
        });
    }

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
