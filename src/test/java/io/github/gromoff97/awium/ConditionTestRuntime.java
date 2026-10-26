package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class ConditionTestRuntime {

    static Condition<Object, Object> diagnosticCondition(String description, String explanation, Condition.Reference<?> reference) {
        return new Condition<>(description, explanation, reference,
                () -> actual -> AttemptResult.of(ConditionResult.satisfied(actual)));
    }

    static <S, R> ConditionResult<? extends R> evaluate(
            Condition<? super S, ? extends R> condition, S actual) {
        return evaluated(new ConditionAwaitAttempt<>(condition).probe(actual));
    }

    static <S> ConditionResult<? extends S> evaluate(PreservingCondition<? super S> condition, S actual) {
        return evaluated(ConditionAwaitAttempt.<S, S>bind(condition.definition).probe(actual));
    }

    static <S> ConditionResult<?> evaluate(
            SelectedCondition<? super S, ?, ?> condition, S actual) {
        return evaluated(new ConditionAwaitAttempt<>(condition.definition).probe(actual));
    }

    static <S> ConditionResult<?> evaluate(MapValueCondition<? super S, ?, ?> condition, S actual) {
        return evaluated(new ConditionAwaitAttempt<>(condition.definition).probe(actual));
    }

    static <RESULT> ConditionResult<? extends RESULT> evaluated(AttemptResult<? extends RESULT> attempt) {
        return switch (attempt) {
            case AttemptResult.Evaluated<? extends RESULT> value -> value.result();
            case AttemptResult.Uncontrolled<?> failure -> throw new AssertionError("condition failed", failure.cause());
        };
    }

    static String description(SelectedCondition<?, ?, ?> condition) {
        return condition.definition.description();
    }

    static String explanation(SelectedCondition<?, ?, ?> condition) {
        return condition.definition.explanation();
    }

    static String explanation(MapValueCondition<?, ?, ?> condition) {
        return condition.definition.explanation();
    }

    static <S> ConditionResult<? extends S> evaluate(ComparisonCondition<? extends S> condition, S actual) {
        return evaluated(ConditionAwaitAttempt.<S, S>bind(condition.definition).probe(actual));
    }

    static String description(ComparisonCondition<?> condition) {
        return condition.definition.description();
    }

    static String explanation(ComparisonCondition<?> condition) {
        return condition.definition.explanation();
    }

    static String description(PreservingCondition<?> condition) {
        return condition.definition.description();
    }

    static String explanation(PreservingCondition<?> condition) {
        return condition.definition.explanation();
    }

    static void assertUnsatisfied(ConditionResult<?> evaluation) {
        assertInstanceOf(ConditionResult.Unsatisfied.class, evaluation);
    }

    static <RESULT> RESULT result(ConditionResult<? extends RESULT> evaluation) {
        assertInstanceOf(ConditionResult.Satisfied.class, evaluation);
        return ((ConditionResult.Satisfied<? extends RESULT>) evaluation).result();
    }

    static String mismatch(ConditionResult<?> evaluation) {
        return assertInstanceOf(ConditionResult.Unsatisfied.class, evaluation).mismatch();
    }

    @SuppressWarnings("unchecked")
    static <VALUE, FAILURE extends Throwable> VALUE throwUnchecked(Throwable failure) throws FAILURE {
        throw (FAILURE) failure;
    }

    private ConditionTestRuntime() {}
}
