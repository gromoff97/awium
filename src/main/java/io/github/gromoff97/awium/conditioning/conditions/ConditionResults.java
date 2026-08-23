package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;

import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.uncontrolled;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.condition;

final class ConditionResults {

    private ConditionResults() {
        throw new AssertionError("Utility class");
    }

    static <R> Evaluation<R> copy(Evaluation<? extends R> evaluation) {
        return switch (evaluation.status()) {
            case SATISFIED -> satisfied(evaluation.result());
            case UNSATISFIED -> evaluation.assertionCause() == null
                    ? unsatisfied(evaluation.mismatch())
                    : assertionUnsatisfied(evaluation.mismatch(), evaluation.assertionCause());
            case UNCONTROLLED -> uncontrolled(evaluation.uncontrolledCause());
        };
    }

    static <R> Evaluation<R> failure(Evaluation<?> evaluation) {
        return switch (evaluation.status()) {
            case UNSATISFIED -> evaluation.assertionCause() == null
                    ? unsatisfied(evaluation.mismatch())
                    : assertionUnsatisfied(evaluation.mismatch(), evaluation.assertionCause());
            case UNCONTROLLED -> uncontrolled(evaluation.uncontrolledCause());
            case SATISFIED -> throw new IllegalArgumentException("evaluation must not be satisfied");
        };
    }

    static <T> Condition<T, T> preserve(Condition<? super T, ?> nested) {
        return condition(nested.description(), actual -> {
            Evaluation<?> evaluation = nested.evaluate(actual);
            return evaluation.status() == Evaluation.Status.SATISFIED
                    ? satisfied(actual) : failure(evaluation);
        });
    }
}
