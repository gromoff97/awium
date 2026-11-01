package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;

import static io.github.gromoff97.awium.internal.FailureDiagnostics.failure;
import static io.github.gromoff97.awium.internal.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static io.github.gromoff97.awium.result.AttemptResult.Satisfied;
import static java.util.Objects.requireNonNull;

final class ConditionRun<OBSERVED, RESULT> {

    final AwaitCondition condition;
    private final Condition<OBSERVED, RESULT> check;
    private final ConditionExecution<OBSERVED, RESULT> evaluation;
    private final AttemptHistory<OBSERVED> history = new AttemptHistory<>();
    private AttemptResult<? extends RESULT> latestResult;

    ConditionRun(AwaitCondition condition) {
        this.condition = condition;
        check = ConditionExecution.prepare(condition);
        evaluation = new ConditionExecution<>(check::newAttempt);
    }

    AttemptResult<? extends RESULT> probe(OBSERVED actual) throws Throwable {
        latestResult = null;
        latestResult = evaluation.probe(actual);
        return latestResult;
    }

    AttemptResult<?> historyResult() throws Throwable {
        return requireNonNull(check.historyResult(latestResult), "condition returned null history result");
    }

    void record(Attempt<OBSERVED> attempt) {
        history.add(check.historySlot(attempt), attempt);
    }

    AwaitResult<OBSERVED, RESULT> finish(WaitCompletion outcome, WaitConfiguration configuration) {
        var conditions = history.conditions(check.historyConditions(condition));
        return outcome instanceof SatisfiedWait
                ? new Succeeded<>(conditions, ((Satisfied<? extends RESULT>) latestResult).value())
                : new Failed<>(conditions, failure(this, outcome, configuration, history.count(), conditions));
    }

    Diagnostic diagnostics(Attempt<?> attempt) { return check.diagnostics(attempt); }
}
