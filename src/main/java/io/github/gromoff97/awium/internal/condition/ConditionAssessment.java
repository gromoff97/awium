package io.github.gromoff97.awium.internal.condition;

import io.github.gromoff97.awium.condition.ConditionEvaluation;
import io.github.gromoff97.awium.results.AwaitAttempt;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.uncontrolled;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.unsatisfied;

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

    public <Next> ConditionAssessment<Next> flatMap(Function<? super Result,
            ? extends ConditionAssessment<? extends Next>> continuation) {
        requireNonNull(continuation, "continuation must not be null");
        return switch (evaluation) {
            case null -> new ConditionAssessment<>(null, context);
            case ConditionEvaluation.Satisfied<? extends Result> value ->
                    typed(requireNonNull(continuation.apply(value.result()), "condition returned null ConditionAssessment"));
            case ConditionEvaluation.Unsatisfied<?> value -> new ConditionAssessment<>(unsatisfied(value.mismatch()), context);
            case ConditionEvaluation.AssertionUnsatisfied<?> value ->
                    new ConditionAssessment<>(assertionUnsatisfied(value.mismatch(), value.cause()), context);
            case ConditionEvaluation.Uncontrolled<?> value -> new ConditionAssessment<>(uncontrolled(value.cause()), context);
        };
    }

    public <Next> ConditionAssessment<Next> mapEvaluation(Function<? super Result,
            ? extends ConditionEvaluation<? extends Next>> continuation) {
        requireNonNull(continuation, "continuation must not be null");
        return new ConditionAssessment<>(evaluation == null ? null : evaluation.continueIfSatisfied(continuation), context);
    }

    public ConditionAssessment<Result> withContext(AwaitAttempt.Context replacement) {
        return new ConditionAssessment<>(evaluation, replacement);
    }

    private static <Result> ConditionAssessment<Result> typed(ConditionAssessment<? extends Result> assessment) {
        return new ConditionAssessment<>(assessment.evaluation(), assessment.context());
    }
}
