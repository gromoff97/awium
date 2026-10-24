package io.github.gromoff97.awium;

@FunctionalInterface
interface AwaitAttempt<OBSERVED, RESULT> {

    AttemptResult<? extends RESULT> probe(OBSERVED actual) throws Throwable;
}
