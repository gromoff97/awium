package io.github.gromoff97.awium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.NarrowingCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;

public final class ConditionTestRuntime {

    public static <S, R> ConditionResult<? extends R> evaluate(
            Condition<? super S, ? extends R> condition, S actual) {
        return condition.newSession().apply(actual);
    }

    public static <S> ConditionResult<? extends S> evaluate(PreservingCondition<? super S> condition, S actual) {
        return condition.<S>preservingSession().apply(actual);
    }

    public static <S, T extends S> ConditionResult<? extends S> evaluate(ExpectedCondition<T> condition, S actual) {
        return condition.<S>preservingSession().apply(actual);
    }

    public static <S, R> ConditionResult<? extends R> evaluate(NarrowingCondition<R> condition, S actual) {
        return condition.newSession().apply(actual);
    }

    public static <S, R, F extends Source<?>> ConditionResult<? extends R> evaluate(
            SelectedCondition<? super S, F> condition, S actual) {
        return condition.<R>selectionSession().apply(actual);
    }

    public static String description(ConditionDefinition<?, ?> condition) {
        return condition.metadata.description();
    }

    public static String explanation(ConditionDefinition<?, ?> condition) {
        return condition.metadata.explanation();
    }

    public static void assertUnsatisfied(ConditionResult<?> evaluation) {
        assertFalse(mismatch(evaluation).isBlank());
    }

    public static Object result(ConditionResult<?> evaluation) {
        return assertInstanceOf(ConditionResult.Satisfied.class, evaluation).result();
    }

    public static String mismatch(ConditionResult<?> evaluation) {
        return assertInstanceOf(ConditionResult.Unsatisfied.class, evaluation).mismatch();
    }

    private ConditionTestRuntime() {
        throw new AssertionError("Utility class");
    }
}
