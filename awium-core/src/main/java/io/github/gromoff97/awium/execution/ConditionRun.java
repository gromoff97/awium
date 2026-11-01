package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;

import static io.github.gromoff97.awium.execution.FailureDiagnostics.failure;
import static io.github.gromoff97.awium.execution.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;

public abstract class ConditionRun<OBSERVED, RESULT> {

    protected final AwaitCondition condition;
    private final AttemptHistory<OBSERVED> history;

    protected ConditionRun(AwaitCondition condition) {
        this.condition = condition;
        history = new AttemptHistory<>();
    }

    public void beginAttempt() {}

    public abstract AttemptResult<?> probe(OBSERVED actual) throws Throwable;

    public abstract boolean complete();

    public abstract RESULT result();

    protected int historySlot() { return 1; }

    protected List<? extends AwaitCondition> historyConditions() { return List.of(condition); }

    public void record(Attempt<OBSERVED> attempt) {
        history.add(historySlot(), attempt);
    }

    public AwaitResult<OBSERVED, RESULT> finish(WaitCompletion outcome, WaitTiming configuration) {
        var conditions = history.conditions(historyConditions());
        return outcome instanceof SatisfiedWait
                ? new Succeeded<>(conditions, result())
                : new Failed<>(conditions, failure(this, outcome, configuration, history.count(), conditions));
    }

    protected Diagnostic diagnostics(Attempt<?> attempt) { return Diagnostic.EMPTY; }
}
