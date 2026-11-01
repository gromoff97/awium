package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;

import static io.github.gromoff97.awium.execution.FailureDiagnostics.failure;
import static io.github.gromoff97.awium.execution.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static io.github.gromoff97.awium.result.AttemptResult.Satisfied;
import static java.util.Objects.requireNonNull;

public final class ConditionRun<OBSERVED, RESULT> {

    final AwaitCondition condition;
    private final Condition<OBSERVED, RESULT> check;
    private final ConditionExecution<OBSERVED, RESULT> evaluation;
    private final AttemptHistory<OBSERVED> history = new AttemptHistory<>();
    private AttemptResult<? extends RESULT> latestResult;

    public ConditionRun(AwaitCondition condition) {
        this.condition = condition;
        check = ConditionExecution.prepare(condition);
        evaluation = new ConditionExecution<>(check::newAttempt);
    }

    public AttemptResult<? extends RESULT> probe(OBSERVED actual) throws Throwable {
        latestResult = null;
        latestResult = evaluation.probe(actual);
        return latestResult;
    }

    public AttemptResult<?> historyResult() throws Throwable {
        return requireNonNull(check.historyResult(latestResult), "condition returned null history result");
    }

    public RESULT result() { return ((Satisfied<? extends RESULT>) latestResult).value(); }

    public void record(Attempt<OBSERVED> attempt) {
        history.add(check.historySlot(attempt), attempt);
    }

    public AwaitResult<OBSERVED, RESULT> finish(WaitCompletion outcome, WaitTiming configuration) {
        var conditions = history.conditions(check.historyConditions(condition));
        return outcome instanceof SatisfiedWait
                ? new Succeeded<>(conditions, result())
                : new Failed<>(conditions, failure(this, outcome, configuration, history.count(), conditions));
    }

    Diagnostic diagnostics(Attempt<?> attempt) { return check.diagnostics(attempt); }
}
