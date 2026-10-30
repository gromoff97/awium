package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptResult;

import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;
import static io.github.gromoff97.awium.ConditionSupport.bind;

final class ConditionTestRuntime {

    static Condition.Typed<Object, Object> diagnosticCondition(String description, Condition.Reference<?> reference) {
        return new Condition.Typed<>(description, reference, () -> AttemptResult::satisfied);
    }

    static <SOURCE, RESULT> AttemptResult<? extends RESULT> evaluate(Condition.Typed<? super SOURCE, ? extends RESULT> condition, SOURCE actual) {
        return evaluated(bind(condition).create(actual).call());
    }

    static <SOURCE> AttemptResult<? extends SOURCE> evaluate(PreservingCondition<? super SOURCE> condition, SOURCE actual) {
        return evaluated(ConditionSupport.<SOURCE, SOURCE>bind(condition).create(actual).call());
    }

    static <SOURCE> AttemptResult<? extends SOURCE> evaluate(Condition.ComparisonCondition<? extends SOURCE> condition, SOURCE actual) {
        return evaluated(ConditionSupport.<SOURCE, SOURCE>bind(condition).create(actual).call());
    }

    static <RESULT> AttemptResult<? extends RESULT> evaluated(AttemptResult<? extends RESULT> attempt) {
        if (attempt instanceof AttemptResult.Uncontrolled<?> failure) {
            throw new AssertionError("condition failed", failure.cause());
        }
        return attempt;
    }

    static void assertUnsatisfied(AttemptResult<?> evaluation) {
        assertInstanceOf(AttemptResult.Unsatisfied.class, evaluation);
    }

    static <RESULT> RESULT result(AttemptResult<? extends RESULT> evaluation) {
        return evaluation instanceof AttemptResult.Satisfied<? extends RESULT> success
                ? success.value() : fail("condition was not satisfied");
    }

    static String mismatch(AttemptResult<?> evaluation) {
        return assertInstanceOf(AttemptResult.Unsatisfied.class, evaluation).mismatch();
    }

    private ConditionTestRuntime() {}
}
