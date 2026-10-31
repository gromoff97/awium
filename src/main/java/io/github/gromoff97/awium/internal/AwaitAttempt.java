package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.ConditionResult;

@FunctionalInterface
public interface AwaitAttempt<OBSERVED, RESULT> {

    ConditionResult<? extends RESULT> probe(OBSERVED actual) throws Throwable;
}
