package io.github.gromoff97.awium.function;

import io.github.gromoff97.awium.result.AttemptResult;

@FunctionalInterface
public interface AwaitAttempt<INPUT, RESULT> {

    AttemptResult<? extends RESULT> probe(INPUT actual) throws Throwable;
}
