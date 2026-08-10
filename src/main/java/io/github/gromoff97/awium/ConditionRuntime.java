package io.github.gromoff97.awium;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

record ConditionRuntime<S, R>(
        Evaluator<S, R> evaluator,
        Description description,
        String explanation) {

    @FunctionalInterface
    interface Evaluator<S, R> {
        Evaluation<R> evaluate(S actual) throws Exception;
    }

    @FunctionalInterface
    interface Description extends Supplier<String> {
    }

    ConditionRuntime {
        Objects.requireNonNull(evaluator);
        Objects.requireNonNull(description);
    }

    Evaluation<R> evaluate(S actual) throws Exception {
        return evaluator.evaluate(actual);
    }

    ConditionRuntime<S, R> explained(String value) {
        return new ConditionRuntime<>(evaluator, description, value);
    }

    static <S, R> ConditionRuntime<S, R> open(
            Condition<? super S, ? extends R> condition) {
        return new ConditionRuntime<>(
                actual -> Evaluation.narrow(condition.evaluate(actual)),
                condition::description, null);
    }

    static <S, R> ConditionRuntime<S, R> open(
            Condition.Explained<? super S, ? extends R> condition) {
        return ConditionRuntime.<S, R>open(condition.delegate())
                .explained(condition.explanation());
    }

    static <S> ConditionRuntime<S, S> preserving(
            PreservingCondition<? super S> condition) {
        ConditionRuntime<? super S, ?> runtime = condition.runtime();
        return new ConditionRuntime<>(actual -> withResult(
                runtime.evaluate(actual), actual), runtime.description(),
                runtime.explanation());
    }

    static <S> ConditionRuntime<S, S> preserving(
            PreservingCondition.Explained<? super S> condition) {
        return ConditionRuntime.<S>preserving(condition.delegate())
                .explained(condition.explanation());
    }

    static <T> ConditionRuntime<Optional<T>, T> present(Present condition) {
        ConditionRuntime<Optional<?>, Object> runtime = condition.runtime();
        return new ConditionRuntime<>(actual -> {
            Evaluation<Object> evaluation = runtime.evaluate(actual);
            if (evaluation != null
                    && evaluation.status() == Evaluation.Status.SATISFIED) {
                return Evaluation.satisfied(actual.orElse(null));
            }
            return withResult(evaluation, null);
        }, runtime.description(), runtime.explanation());
    }

    static <T> ConditionRuntime<Optional<T>, T> present(
            Present.Explained condition) {
        return ConditionRuntime.<T>present(condition.delegate())
                .explained(condition.explanation());
    }

    static <S> ConditionRuntime<S, S> structural(
            StructuralCondition condition, String subject,
            ToIntFunction<? super S> size) {
        return new ConditionRuntime<>(actual -> {
            if (actual == null) {
                return Evaluation.unsatisfied(subject + " was null");
            }
            return condition.evaluate(size.applyAsInt(actual), actual, subject);
        }, () -> condition.description(subject), null);
    }

    static <S> ConditionRuntime<S, S> structural(
            StructuralCondition.Explained condition, String subject,
            ToIntFunction<? super S> size) {
        return ConditionRuntime.<S>structural(
                condition.delegate(), subject, size)
                .explained(condition.explanation());
    }

    private static <R> Evaluation<R> withResult(
            Evaluation<?> evaluation, R satisfiedResult) {
        if (evaluation == null) {
            return null;
        }
        return switch (evaluation.status()) {
            case SATISFIED -> Evaluation.satisfied(satisfiedResult);
            case UNSATISFIED -> evaluation.assertionCause() == null
                    ? Evaluation.unsatisfied(evaluation.mismatch())
                    : Evaluation.assertionUnsatisfied(
                            evaluation.mismatch(), evaluation.assertionCause());
            case UNCONTROLLED -> Evaluation.uncontrolled(
                    evaluation.uncontrolledCause());
        };
    }
}
