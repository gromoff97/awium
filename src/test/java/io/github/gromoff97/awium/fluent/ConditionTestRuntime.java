package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
import io.github.gromoff97.awium.fluent.Condition.PreservingStage;
import io.github.gromoff97.awium.fluent.Condition.ExpectedStage;
import io.github.gromoff97.awium.fluent.Condition.NarrowingStage;
import io.github.gromoff97.awium.fluent.Condition.SelectedStage;
import io.github.gromoff97.awium.fluent.ConditionStage.ResultStage;
import io.github.gromoff97.awium.sources.Source;

public final class ConditionTestRuntime {

    public static <S, R> ConditionEvaluation<R> evaluate(
            ResultStage<? super S, ? extends R> condition, S actual) {
        ConditionEvaluation<? extends R> evaluation = ConditionRuntime.<S, R>evaluator(condition).apply(actual);
        return evaluation == null ? null : evaluation.continueIfSatisfied(ConditionEvaluation::satisfied);
    }

    public static <S> ConditionEvaluation<S> evaluate(PreservingStage<? super S> condition, S actual) {
        return ConditionRuntime.<S>preservingEvaluator(condition).apply(actual);
    }

    public static <S, T extends S> ConditionEvaluation<S> evaluate(ExpectedStage<T> condition, S actual) {
        return ConditionRuntime.<S>expectedEvaluator(condition).apply(actual);
    }

    public static <S, R> ConditionEvaluation<R> evaluate(NarrowingStage<R> condition, S actual) {
        return ConditionRuntime.<S, R>narrowingEvaluator(condition).apply(actual);
    }

    public static <S, R, F extends Source<?>> ConditionEvaluation<R> evaluate(
            SelectedStage<? super S, F> condition, S actual) {
        return ConditionRuntime.<S, R>selectedEvaluator(condition).apply(actual);
    }

    public static String description(AwaitCondition condition) {
        return ConditionRuntime.description(condition);
    }

    public static String explanation(AwaitCondition condition) {
        return ConditionRuntime.explanation(condition);
    }

    public static Object result(ConditionEvaluation<?> evaluation) {
        return switch (evaluation) {
            case ConditionEvaluation.Satisfied<?> satisfied -> satisfied.result();
            default -> throw new AssertionError("evaluation is not satisfied: " + evaluation);
        };
    }

    public static String mismatch(ConditionEvaluation<?> evaluation) {
        return switch (evaluation) {
            case ConditionEvaluation.Unsatisfied<?> unsatisfied -> unsatisfied.mismatch();
            case ConditionEvaluation.AssertionUnsatisfied<?> unsatisfied -> unsatisfied.mismatch();
            default -> throw new AssertionError("evaluation is not unsatisfied: " + evaluation);
        };
    }

    private ConditionTestRuntime() {
        throw new AssertionError("Utility class");
    }
}
