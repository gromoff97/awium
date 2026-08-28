package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.AwaitCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime;
import io.github.gromoff97.awium.sources.Source;

final class ConditionTestRuntime {

    static <S, R> Evaluation<R> evaluate(
            ResultStage<? super S, ? extends R> condition, S actual) {
        Evaluation<? extends R> evaluation = ConditionRuntime.<S, R>evaluator(condition).apply(actual);
        return evaluation == null ? null : evaluation.continueIfSatisfied(Evaluation::satisfied);
    }

    static <S> Evaluation<S> evaluate(PreservingStage<? super S> condition, S actual) {
        return ConditionRuntime.<S>preservingEvaluator(condition).apply(actual);
    }

    static <S, R, F extends Source<?>> Evaluation<R> evaluate(
            SelectedStage<? super S, F> condition, S actual) {
        return ConditionRuntime.<S, R>selectedEvaluator(condition).apply(actual);
    }

    static String description(AwaitCondition condition) {
        return ConditionRuntime.description(condition);
    }

    static String explanation(AwaitCondition condition) {
        return ConditionRuntime.explanation(condition);
    }

    private ConditionTestRuntime() {
        throw new AssertionError("Utility class");
    }
}
