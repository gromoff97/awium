package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.narrow;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.uncontrolled;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static java.util.Objects.requireNonNull;

public record RuntimeCondition<S, R>(
        CheckedFunction<S, Evaluation<R>> evaluator,
        Supplier<String> description,
        String explanation) {

    public RuntimeCondition {
        requireNonNull(evaluator);
        requireNonNull(description);
    }

    public Evaluation<R> evaluate(S actual) throws Exception {
        return evaluator.apply(actual);
    }

    public RuntimeCondition<S, R> explained(String value) {
        return new RuntimeCondition<>(evaluator, description, value);
    }

    public static <S, R> RuntimeCondition<S, R> open(
            Condition<? super S, ? extends R> condition) {
        return new RuntimeCondition<>(
                actual -> narrow(condition.evaluate(actual)),
                condition::description, null);
    }

    public static <S, R> RuntimeCondition<S, R> open(
            Condition.ExplainedCondition<? super S, ? extends R> condition) {
        return RuntimeCondition.<S, R>open(condition.delegate())
                .explained(condition.explanation());
    }

    public static <S> RuntimeCondition<S, S> preserving(
            PreservingCondition<? super S> condition) {
        RuntimeCondition<? super S, ?> runtime = condition.runtime();
        return new RuntimeCondition<>(actual -> withResult(
                runtime.evaluate(actual), actual), runtime.description(),
                runtime.explanation());
    }

    public static <S> RuntimeCondition<S, S> preserving(
            PreservingCondition.ExplainedCondition<? super S> condition) {
        return RuntimeCondition.<S>preserving(condition.delegate())
                .explained(condition.explanation());
    }

    public static <T> RuntimeCondition<Optional<T>, T> present(
            PresentCondition condition) {
        RuntimeCondition<Optional<?>, Object> runtime = condition.runtime();
        return new RuntimeCondition<>(actual -> {
            Evaluation<Object> evaluation = runtime.evaluate(actual);
            if (evaluation != null
                    && evaluation.status() == Evaluation.Status.SATISFIED) {
                return satisfied(actual.orElse(null));
            }
            return withResult(evaluation, null);
        }, runtime.description(), runtime.explanation());
    }

    public static <T> RuntimeCondition<Optional<T>, T> present(
            PresentCondition.ExplainedCondition condition) {
        return RuntimeCondition.<T>present(condition.delegate())
                .explained(condition.explanation());
    }

    public static <S> RuntimeCondition<S, S> structural(
            StructuralCondition condition, String subject,
            ToIntFunction<? super S> size) {
        return new RuntimeCondition<>(actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            return condition.evaluate(size.applyAsInt(actual), actual, subject);
        }, () -> condition.description(subject), null);
    }

    public static <S> RuntimeCondition<S, S> structural(
            StructuralCondition.ExplainedCondition condition, String subject,
            ToIntFunction<? super S> size) {
        return RuntimeCondition.<S>structural(
                condition.delegate(), subject, size)
                .explained(condition.explanation());
    }

    private static <R> Evaluation<R> withResult(
            Evaluation<?> evaluation, R satisfiedResult) {
        if (evaluation == null) {
            return null;
        }
        return switch (evaluation.status()) {
            case SATISFIED -> satisfied(satisfiedResult);
            case UNSATISFIED -> evaluation.assertionCause() == null
                    ? unsatisfied(evaluation.mismatch())
                    : assertionUnsatisfied(
                            evaluation.mismatch(), evaluation.assertionCause());
            case UNCONTROLLED -> uncontrolled(
                    evaluation.uncontrolledCause());
        };
    }

    static String literalExplanation(String explanation) {
        return nonBlank(explanation, "explanation");
    }

    static String formattedExplanation(String format, Object[] arguments) {
        requireNonNull(format, "format must not be null");
        requireNonNull(arguments, "arguments must not be null");
        return nonBlank(String.format(Locale.ROOT, format, arguments),
                "explanation");
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
