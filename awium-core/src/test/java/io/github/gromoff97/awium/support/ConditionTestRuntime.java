package io.github.gromoff97.awium.support;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.result.ConditionResult;

import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static org.junit.jupiter.api.Assertions.fail;

public final class ConditionTestRuntime {

    public static Condition<Object, Object> diagnosticCondition(String description, Reference<?> reference) {
        return new DefinedCondition<>(description, reference, () -> ConditionResult::satisfied);
    }

    public static <RESULT> RESULT result(ConditionResult<? extends RESULT> evaluation) {
        return evaluation instanceof Satisfied<? extends RESULT> success
                ? success.value() : fail("condition was not satisfied");
    }

    private ConditionTestRuntime() {}
}
