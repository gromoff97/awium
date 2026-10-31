package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptResult;

import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Condition.ComparisonCondition;
import static io.github.gromoff97.awium.Condition.Reference;
import static io.github.gromoff97.awium.Condition.Typed;
import static io.github.gromoff97.awium.internal.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.internal.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.internal.AttemptResult.Uncontrolled;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;
import static io.github.gromoff97.awium.ConditionSupport.bind;

final class ConditionTestRuntime {

    static Typed<Object, Object> diagnosticCondition(String description, Reference<?> reference) {
        return new Typed<>(description, reference, () -> AttemptResult::satisfied);
    }

    static <SOURCE, RESULT> AttemptResult<? extends RESULT> evaluate(Typed<? super SOURCE, ? extends RESULT> condition, SOURCE actual) {
        return evaluated(bind(condition).create(actual).call());
    }

    static <SOURCE> AttemptResult<? extends SOURCE> evaluate(PreservingCondition<? super SOURCE> condition, SOURCE actual) {
        return evaluated(ConditionSupport.<SOURCE, SOURCE>bind(condition).create(actual).call());
    }

    static <SOURCE> AttemptResult<? extends SOURCE> evaluate(ComparisonCondition<? extends SOURCE> condition, SOURCE actual) {
        return evaluated(ConditionSupport.<SOURCE, SOURCE>bind(condition).create(actual).call());
    }

    static <RESULT> AttemptResult<? extends RESULT> evaluated(AttemptResult<? extends RESULT> attempt) {
        return switch (attempt) {
            case Uncontrolled<?> failure -> throw new AssertionError("condition failed", failure.cause());
            default -> attempt;
        };
    }

    static void assertUnsatisfied(AttemptResult<?> evaluation) {
        assertInstanceOf(Unsatisfied.class, evaluation);
    }

    static <RESULT> RESULT result(AttemptResult<? extends RESULT> evaluation) {
        return evaluation instanceof Satisfied<? extends RESULT> success
                ? success.value() : fail("condition was not satisfied");
    }

    static String mismatch(AttemptResult<?> evaluation) {
        return assertInstanceOf(Unsatisfied.class, evaluation).mismatch();
    }

    private ConditionTestRuntime() {}
}
