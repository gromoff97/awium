package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;

public interface ConditionRun<OBSERVED, RESULT> {

    void beginAttempt();

    boolean complete();

    RESULT result();

    ConditionResult<?> probe(OBSERVED actual) throws Throwable;

    void record(Attempt<OBSERVED> attempt);

    AwaitResult<OBSERVED, RESULT> finish(WaitCompletion outcome, WaitTiming configuration);
}
