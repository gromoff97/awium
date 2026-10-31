package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;

import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Condition.ComparisonCondition;
import static io.github.gromoff97.awium.Condition.Reference;
import static io.github.gromoff97.awium.Condition.Typed;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;
import static io.github.gromoff97.awium.ConditionSupport.bind;

final class ConditionTestRuntime {

    static Typed<Object, Object> diagnosticCondition(String description, Reference<?> reference) {
        return new Typed<>(description, reference, () -> ConditionResult::satisfied);
    }

    static <SOURCE, RESULT> ConditionResult<? extends RESULT> evaluate(Typed<? super SOURCE, ? extends RESULT> condition, SOURCE actual) {
        return assertDoesNotThrow(() -> bind(condition).probe(actual));
    }

    static <SOURCE> ConditionResult<? extends SOURCE> evaluate(PreservingCondition<? super SOURCE> condition, SOURCE actual) {
        return assertDoesNotThrow(() -> ConditionSupport.<SOURCE, SOURCE>bind(condition).probe(actual));
    }

    static <SOURCE> ConditionResult<? extends SOURCE> evaluate(ComparisonCondition<? extends SOURCE> condition, SOURCE actual) {
        return assertDoesNotThrow(() -> ConditionSupport.<SOURCE, SOURCE>bind(condition).probe(actual));
    }

    static void assertUnsatisfied(ConditionResult<?> evaluation) {
        assertInstanceOf(Unsatisfied.class, evaluation);
    }

    static <RESULT> RESULT result(ConditionResult<? extends RESULT> evaluation) {
        return evaluation instanceof Satisfied<? extends RESULT> success
                ? success.value() : fail("condition was not satisfied");
    }

    static String mismatch(ConditionResult<?> evaluation) {
        return assertInstanceOf(Unsatisfied.class, evaluation).mismatch();
    }

    private ConditionTestRuntime() {}
}
