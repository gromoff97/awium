package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.Condition.SourceCondition;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class ConditionTestRuntime {

    static Condition.Typed<Object, Object> diagnosticCondition(String description, String explanation, Condition.Reference<?> reference) {
        return new Condition.Typed<>(description, explanation, reference,
                () -> actual -> AttemptResult.of(ConditionResult.satisfied(actual)));
    }

    static <S, R> ConditionResult<? extends R> evaluate(
            Condition.Typed<? super S, ? extends R> condition, S actual) {
        return evaluated(new ConditionAwaitAttempt<>(condition).probe(actual));
    }

    static <S> ConditionResult<? extends S> evaluate(
            SourceCondition<? super S, ? super Reference<? super S>, ?> condition, S actual) {
        return evaluated(ConditionAwaitAttempt.<S, S>bind(condition).probe(actual));
    }

    static <S> ConditionResult<?> evaluate(
            SelectedCondition<? super S, ?, ?> condition, S actual) {
        return evaluated(ConditionAwaitAttempt.<S, Object>bind(condition).probe(actual));
    }

    static <S> ConditionResult<?> evaluate(MapValueCondition<? super S, ?, ?> condition, S actual) {
        return evaluated(ConditionAwaitAttempt.<S, Object>bind(condition).probe(actual));
    }

    static <RESULT> ConditionResult<? extends RESULT> evaluated(AttemptResult<? extends RESULT> attempt) {
        return switch (attempt) {
            case AttemptResult.Evaluated<? extends RESULT> value -> value.result();
            case AttemptResult.Uncontrolled<?> failure -> throw new AssertionError("condition failed", failure.cause());
        };
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
