package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class ConditionTestRuntime {

    static <S, R> ConditionResult<? extends R> evaluate(
            Condition<? super S, ? extends R> condition, S actual) {
        return condition.newSession().apply(actual);
    }

    static <S> ConditionResult<? extends S> evaluate(PreservingCondition<? super S> condition, S actual) {
        return condition.<S>preservingSession().apply(actual);
    }

    static <S, R> ConditionResult<? extends R> evaluate(
            SelectedCondition<? super S> condition, S actual) {
        return condition.<R>selectionSession().apply(actual);
    }

    static String description(Condition<?, ?> condition) {
        return condition.metadata.description();
    }

    static String explanation(Condition<?, ?> condition) {
        return condition.metadata.explanation();
    }

    static void assertUnsatisfied(ConditionResult<?> evaluation) {
        assertFalse(mismatch(evaluation).isBlank());
    }

    static Object result(ConditionResult<?> evaluation) {
        return assertInstanceOf(ConditionResult.Satisfied.class, evaluation).result();
    }

    static String mismatch(ConditionResult<?> evaluation) {
        return assertInstanceOf(ConditionResult.Unsatisfied.class, evaluation).mismatch();
    }

    private ConditionTestRuntime() {}
}
