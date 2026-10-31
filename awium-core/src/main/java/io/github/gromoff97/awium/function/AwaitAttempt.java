package io.github.gromoff97.awium.function;

import io.github.gromoff97.awium.result.ConditionResult;

@FunctionalInterface
public interface AwaitAttempt<INPUT, RESULT> {

    ConditionResult<? extends RESULT> probe(INPUT actual) throws Throwable;
}
