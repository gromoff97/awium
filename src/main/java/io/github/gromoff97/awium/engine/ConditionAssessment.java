package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
import io.github.gromoff97.awium.results.AwaitAttempt;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.uncontrolled;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.unsatisfied;

/**
 * Condition outcome paired with engine diagnostics that do not belong to the public condition SPI.
 *
 * @param <Result> value produced by a satisfied condition
 * @param evaluation condition outcome, or {@code null} when a broken evaluator returned {@code null}
 * @param context attempt diagnostics
 */
public record ConditionAssessment<Result>(ConditionEvaluation<? extends Result> evaluation, AwaitAttempt.Context context) {

    public ConditionAssessment {
        requireNonNull(context, "context must not be null");
    }

    public static <Result> ConditionAssessment<Result> plain(ConditionEvaluation<? extends Result> evaluation) {
        return new ConditionAssessment<>(evaluation, AwaitAttempt.Context.Plain.INSTANCE);
    }

    public static <Current, Next> ConditionAssessment<Next> continueIfSatisfied(ConditionEvaluation<? extends Current> evaluation,
            Function<? super Current, ? extends ConditionAssessment<? extends Next>> continuation) {
        requireNonNull(continuation, "continuation must not be null");
        return switch (evaluation) {
            case null -> plain(null);
            case ConditionEvaluation.Satisfied<? extends Current> value -> typed(continuation.apply(value.result()));
            case ConditionEvaluation.Unsatisfied<?> value -> plain(unsatisfied(value.mismatch()));
            case ConditionEvaluation.AssertionUnsatisfied<?> value ->
                    plain(assertionUnsatisfied(value.mismatch(), value.cause()));
            case ConditionEvaluation.Uncontrolled<?> value -> plain(uncontrolled(value.cause()));
        };
    }

    public <Next> ConditionAssessment<Next> continueIfSatisfied(Function<? super Result,
            ? extends ConditionEvaluation<? extends Next>> continuation) {
        return new ConditionAssessment<>(evaluation == null ? null : evaluation.continueIfSatisfied(continuation), context);
    }

    private static <Result> ConditionAssessment<Result> typed(ConditionAssessment<? extends Result> assessment) {
        return assessment == null ? null : new ConditionAssessment<>(assessment.evaluation(), assessment.context());
    }
}
