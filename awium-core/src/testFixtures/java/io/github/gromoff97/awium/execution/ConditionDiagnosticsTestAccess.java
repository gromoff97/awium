package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.result.AwaitFailure;

public final class ConditionDiagnosticsTestAccess {
    private ConditionDiagnosticsTestAccess() {}

    public static AwaitFailure failure(ConditionRun<?, ?> run, WaitCompletion outcome,
            WaitConfiguration configuration, long attemptNumber) {
        return FailureDiagnostics.failure((AbstractConditionRun<?, ?>) run, outcome, configuration, attemptNumber);
    }
}
