package io.github.gromoff97.awium.function;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.internal.ConditionExecution;
import io.github.gromoff97.awium.result.AttemptResult;

import static io.github.gromoff97.awium.execution.ProbeFailure.locatedFailure;

@FunctionalInterface
public interface AwaitAttempt<INPUT, RESULT> {

    static <INPUT, RESULT> AwaitAttempt<INPUT, RESULT> bind(Condition<? super INPUT, ? extends RESULT> condition) {
        return ConditionExecution.bind(condition);
    }

    static <INPUT> AwaitAttempt<INPUT, ?> bind(AwaitCondition condition) {
        return ConditionExecution.bind(condition);
    }

    AttemptResult<? extends RESULT> probe(INPUT actual) throws Throwable;

    default AttemptResult<? extends RESULT> probeAt(INPUT actual, String location) throws Throwable {
        try {
            return probe(actual);
        } catch (Throwable failure) {
            throw locatedFailure(location, failure);
        }
    }
}
