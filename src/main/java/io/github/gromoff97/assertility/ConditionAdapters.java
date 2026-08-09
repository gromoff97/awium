package io.github.gromoff97.assertility;

import java.util.Optional;

final class ConditionAdapters {

    private ConditionAdapters() {
    }

    static <S, R> ConditionRuntime<S, R> open(
            Condition<? super S, ? extends R> condition) {
        return new ConditionRuntime<>(
                actual -> Evaluation.narrow(condition.evaluate(actual)),
                condition::description, null);
    }

    static <S, R> ConditionRuntime<S, R> open(
            ExplainedCondition<? super S, ? extends R> condition) {
        return ConditionAdapters.<S, R>open(condition.delegate())
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
            ExplainedPreservingCondition<? super S> condition) {
        return ConditionAdapters.<S>preserving(condition.delegate())
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
            ExplainedPresent condition) {
        return ConditionAdapters.<T>present(condition.delegate())
                .explained(condition.explanation());
    }

    static <S> ConditionRuntime<S, S> structural(
            StructuralCondition condition, String nullMismatch) {
        ConditionRuntime<Object, Object> runtime = condition.runtime();
        return new ConditionRuntime<>(actual -> actual == null
                ? Evaluation.unsatisfied(nullMismatch)
                : withResult(runtime.evaluate(actual), actual),
                runtime.description(), runtime.explanation());
    }

    static <S> ConditionRuntime<S, S> structural(
            ExplainedStructuralCondition condition, String nullMismatch) {
        return ConditionAdapters.<S>structural(condition.delegate(), nullMismatch)
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
