package io.github.gromoff97.awium;

import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.result.ConditionResult;

import static io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.execution.ConditionExecution.bind;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

final class ConditionTestRuntime {

    static Condition<Object, Object> diagnosticCondition(String description, Reference<?> reference) {
        return new DefinedCondition<>(description, reference, () -> ConditionResult::satisfied);
    }

    static <SOURCE, RESULT> ConditionResult<? extends RESULT> evaluate(Condition<? super SOURCE, ? extends RESULT> condition, SOURCE actual) {
        return assertDoesNotThrow(() -> bind(condition).probe(actual));
    }

    static <SOURCE> ConditionResult<? extends SOURCE> evaluate(PreservingCondition<? super SOURCE> condition, SOURCE actual) {
        return assertDoesNotThrow(() -> ConditionExecution.<SOURCE, SOURCE>bind(condition).probe(actual));
    }

    static <SOURCE> ConditionResult<? extends SOURCE> evaluate(ComparisonCondition<? extends SOURCE> condition, SOURCE actual) {
        return assertDoesNotThrow(() -> ConditionExecution.<SOURCE, SOURCE>bind(condition).probe(actual));
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
