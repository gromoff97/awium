package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.uncontrolled;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static java.util.Objects.requireNonNull;

public record RuntimeCondition<S, R>(
        CheckedFunction<S, Evaluation<R>> evaluator,
        Supplier<String> description,
        String explanation) {

    public RuntimeCondition {
        requireNonNull(evaluator, "evaluator must not be null");
        requireNonNull(description, "description must not be null");
        if (explanation != null) {
            explanation = literalExplanation(explanation);
        }
    }

    public RuntimeCondition(CheckedFunction<S, Evaluation<R>> evaluator, Supplier<String> description) {
        this(evaluator, description, null);
    }

    public Evaluation<R> evaluate(S actual) throws Exception {
        return evaluator.apply(actual);
    }

    public RuntimeCondition<S, R> explained(String value) {
        return new RuntimeCondition<>(evaluator, description,
                requireNonNull(value, "explanation must not be null"));
    }

    @SuppressWarnings("unchecked")
    public static <S, R> RuntimeCondition<S, R> open(
            Condition<? super S, ? extends R> condition) {
        return new RuntimeCondition<>(actual -> (Evaluation<R>) condition.evaluate(actual), condition::description);
    }

    public static <S> RuntimeCondition<S, S> preserving(
            PreservingCondition<? super S> condition) {
        RuntimeCondition<? super S, ?> runtime = condition.runtime();
        return new RuntimeCondition<>(actual -> withResult(runtime.evaluator().apply(actual), actual),
                runtime.description(), runtime.explanation());
    }

    public static <T> RuntimeCondition<Optional<T>, T> present(
            PresentCondition condition) {
        RuntimeCondition<Optional<?>, Object> runtime = condition.runtime();
        return new RuntimeCondition<>(actual -> {
            Evaluation<Object> evaluation = runtime.evaluator().apply(actual);
            return withResult(evaluation,
                    evaluation != null && evaluation.status() == SATISFIED ? actual.orElse(null) : null);
        }, runtime.description(), runtime.explanation());
    }

    public static <S> RuntimeCondition<S, S> structural(
            StructuralCondition condition, String subject,
            ToIntFunction<? super S> size) {
        requireNonNull(condition, "condition must not be null");
        String validatedSubject = nonBlank(subject, "subject");
        requireNonNull(size, "size function must not be null");
        return new RuntimeCondition<>(actual -> {
            if (actual == null) {
                return unsatisfied(validatedSubject + " was null");
            }
            return condition.evaluate(size.applyAsInt(actual), actual,
                    validatedSubject);
        }, () -> condition.description(validatedSubject));
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
        return String.format(Locale.ROOT, format, arguments);
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
