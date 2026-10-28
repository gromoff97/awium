package io.github.gromoff97.awium.internal;

@FunctionalInterface
public interface AwaitAttempt<OBSERVED, RESULT> {

    ConditionEvaluation<? extends RESULT> probe(OBSERVED actual) throws Throwable;
}
