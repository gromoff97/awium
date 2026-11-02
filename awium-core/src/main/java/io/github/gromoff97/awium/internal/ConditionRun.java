package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import io.github.gromoff97.awium.result.ConditionHistory;
import java.util.List;

import static io.github.gromoff97.awium.internal.FailureDiagnostics.failure;
import static io.github.gromoff97.awium.internal.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.internal.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static io.github.gromoff97.awium.result.AttemptResult.Satisfied;
import static java.util.Objects.requireNonNull;
import static java.lang.Thread.currentThread;

final class ConditionRun<OBSERVED, RESULT> {

    private final AwaitCondition condition;
    private Condition<OBSERVED, RESULT> check;
    private final ConditionExecution<OBSERVED, RESULT> evaluation;
    private final AttemptHistory<OBSERVED> history = new AttemptHistory<>();
    private AttemptResult<? extends RESULT> latestResult;

    ConditionRun(AwaitCondition condition) {
        this.condition = requireNonNull(condition, "condition must not be null");
        evaluation = new ConditionExecution<>(() -> check.newAttempt());
    }

    void initialize() {
        check = ConditionExecution.prepare(condition);
    }

    AttemptResult<? extends RESULT> probe(OBSERVED actual) throws Throwable {
        latestResult = evaluation.probe(actual);
        return latestResult;
    }

    boolean isSatisfied() { return latestResult instanceof Satisfied<?>; }

    AttemptResult<?> historyResult() throws Throwable {
        return requireNonNull(check.historyResult(latestResult), "condition returned null history result");
    }

    int historySlot(Attempt<?> attempt) {
        int slot = check == null ? 1 : check.historySlot(attempt);
        if (slot < 1) throw new IllegalArgumentException("history slot must be positive");
        return slot;
    }

    void record(int slot, Attempt<OBSERVED> attempt) {
        history.add(slot, attempt);
    }

    AwaitResult<OBSERVED, RESULT> finish(WaitCompletion outcome, WaitConfiguration configuration) {
        List<ConditionHistory<OBSERVED>> conditions;
        boolean interrupted = currentThread().isInterrupted();
        try {
            var definitions = check == null ? List.of(condition)
                    : requireNonNull(check.historyConditions(condition), "condition returned null history conditions");
            conditions = history.conditions(definitions);
        } catch (Throwable cause) {
            var step = outcome.step().withConditionFailure(cause, "condition histories");
            outcome = outcome instanceof Uncontrolled failure
                    ? new Uncontrolled(step, failure.interrupted()) : new Uncontrolled(step);
            conditions = List.of(history.flattened(condition));
        } finally {
            if (interrupted) currentThread().interrupt();
        }
        return outcome instanceof SatisfiedWait
                ? new Succeeded<>(conditions, ((Satisfied<? extends RESULT>) latestResult).value())
                : new Failed<>(conditions,
                        failure(condition, this::diagnostics, outcome, configuration, history.count(), conditions));
    }

    private Diagnostic diagnostics(Attempt<?> attempt) { return check == null ? Diagnostic.EMPTY : check.diagnostics(attempt); }
}
