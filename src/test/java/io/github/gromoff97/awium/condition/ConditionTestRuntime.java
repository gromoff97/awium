package io.github.gromoff97.awium.condition;

import io.github.gromoff97.awium.internal.condition.ConditionRuntime;
import io.github.gromoff97.awium.condition.Condition.ExpectedCondition;
import io.github.gromoff97.awium.condition.Condition.NarrowingCondition;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.condition.Condition.SelectedCondition;
import io.github.gromoff97.awium.condition.Condition;

import io.github.gromoff97.awium.sources.Source;

public final class ConditionTestRuntime {

    public static <S, R> ConditionEvaluation<R> evaluate(
            Condition<? super S, ? extends R> condition, S actual) {
        return ConditionRuntime.<S, R>evaluator(condition).apply(actual);
    }

    public static <S> ConditionEvaluation<S> evaluate(PreservingCondition<? super S> condition, S actual) {
        return ConditionRuntime.<S>preservingEvaluator(condition).apply(actual);
    }

    public static <S, T extends S> ConditionEvaluation<S> evaluate(ExpectedCondition<T> condition, S actual) {
        return ConditionRuntime.<S>preservingEvaluator(condition).apply(actual);
    }

    public static <S, R> ConditionEvaluation<R> evaluate(NarrowingCondition<R> condition, S actual) {
        return ConditionRuntime.<S, R>evaluator(condition).apply(actual);
    }

    public static <S, R, F extends Source<?>> ConditionEvaluation<R> evaluate(
            SelectedCondition<? super S, F> condition, S actual) {
        return ConditionRuntime.<S, R>evaluator(condition).apply(actual);
    }

    public static String description(AwaitCondition condition) {
        return ConditionRuntime.metadata(condition).description();
    }

    public static String explanation(AwaitCondition condition) {
        return ConditionRuntime.metadata(condition).explanation();
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
            default -> throw new AssertionError("evaluation is not unsatisfied: " + evaluation);
        };
    }

    private ConditionTestRuntime() {
        throw new AssertionError("Utility class");
    }
}
