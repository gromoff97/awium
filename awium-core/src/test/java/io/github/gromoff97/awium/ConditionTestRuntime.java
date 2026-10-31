package io.github.gromoff97.awium;

import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.result.ConditionResult;

import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static org.junit.jupiter.api.Assertions.fail;

final class ConditionTestRuntime {

    static Condition<Object, Object> diagnosticCondition(String description, Reference<?> reference) {
        return new DefinedCondition<>(description, reference, () -> ConditionResult::satisfied);
    }

    static <RESULT> RESULT result(ConditionResult<? extends RESULT> evaluation) {
        return evaluation instanceof Satisfied<? extends RESULT> success
                ? success.value() : fail("condition was not satisfied");
    }

    private ConditionTestRuntime() {}
}
