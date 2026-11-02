package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ProbeFailure;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionHistory;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.internal.ExecutionStep.Phase;
import static io.github.gromoff97.awium.internal.FailureDiagnostics.failure;
import static io.github.gromoff97.awium.internal.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.internal.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.Attempt.Operation;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

final class AwaitRun<OBSERVED, RESULT> {

    private final Callable<? extends OBSERVED> source;
    private final AwaitCondition definition;
    private final LongSupplier clock;
    private final long started;
    private final GuardedAttempt<OBSERVED, RESULT> evaluation;
    private final AttemptHistory<OBSERVED> history = new AttemptHistory<>();
    private Condition<OBSERVED, RESULT> condition;
    private RESULT selectedValue;

    AwaitRun(Callable<? extends OBSERVED> source, AwaitCondition definition, LongSupplier clock) {
        this.source = source;
        this.definition = requireNonNull(definition, "condition must not be null");
        this.clock = clock;
        evaluation = new GuardedAttempt<>(() -> condition.newAttempt());
        started = clock.getAsLong();
    }

    long elapsedNanos() {
        return clock.getAsLong() - started;
    }

    ExecutionStep<OBSERVED> initialize() {
        try {
            condition = GuardedAttempt.prepare(definition);
            return null;
        } catch (Throwable failure) {
            return failed(Phase.ACQUISITION,
                    new FailedBeforeObservation<>(Operation.CONDITION, failure));
        }
    }

    ExecutionStep<OBSERVED> poll(Phase phase) {
        var interrupted = interruptedBefore(phase);
        if (interrupted != null) return interrupted;
        OBSERVED actual;
        try {
            actual = source.call();
        } catch (Throwable failure) {
            return failed(phase,
                    new FailedBeforeObservation<>(Operation.SOURCE, failure));
        }

        if (currentThread().isInterrupted()) {
            return failed(phase, new FailedAfterObservation<>(actual, Operation.SOURCE,
                    new InterruptedException("caller thread interrupt flag was set"), null));
        }

        Attempt<OBSERVED> attempt;
        Diagnostic diagnostic = Diagnostic.EMPTY;
        Throwable conditionCause = null;
        boolean conditionSatisfied = false;
        try {
            var checked = evaluation.probe(actual);
            if (checked instanceof Satisfied<? extends RESULT> success) {
                selectedValue = success.value();
                conditionSatisfied = true;
            }
            diagnostic = checked.diagnostic();
            conditionCause = checked.cause();
            var recorded = requireNonNull(condition.historyResult(checked), "condition returned null history result");
            diagnostic = recorded.diagnostic();
            attempt = switch (recorded) {
                case Satisfied<?> success -> new SatisfiedObservation<>(actual, success.diagnostic());
                case Unsatisfied<?> mismatch ->
                        new UnsatisfiedObservation<>(actual, mismatch.diagnostic(), mismatch.cause());
            };
        } catch (ProbeFailure failure) {
            attempt = new FailedAfterObservation<>(actual, Operation.CONDITION,
                    failure.getCause(), failure.location(), diagnostic);
        } catch (Throwable failure) {
            attempt = new FailedAfterObservation<>(actual, Operation.CONDITION, failure, null, diagnostic);
        }
        return record(phase, attempt, conditionSatisfied, conditionCause);
    }

    private ExecutionStep<OBSERVED> failed(Phase phase, Failure<OBSERVED> attempt) {
        return record(phase, attempt, false, null);
    }

    private ExecutionStep<OBSERVED> record(Phase phase, Attempt<OBSERVED> attempt, boolean conditionSatisfied, Throwable conditionCause) {
        Throwable routingFailure = null;
        int slot = 1;
        boolean interrupted = currentThread().isInterrupted();
        try {
            int reportedSlot = condition == null ? 1 : condition.historySlot(attempt);
            if (reportedSlot < 1) throw new IllegalArgumentException("history slot must be positive");
            slot = reportedSlot;
        } catch (Throwable failure) {
            routingFailure = failure;
        } finally {
            if (interrupted) currentThread().interrupt();
        }
        var step = new ExecutionStep<>(phase, elapsedNanos(), attempt, conditionSatisfied)
                .withRelatedCause(conditionCause);
        if (routingFailure != null) step = step.withConditionFailure(routingFailure, "condition history");
        if (!(step.reportedAttempt() instanceof Failure<?>) && currentThread().isInterrupted()) {
            var cause = conditionCause == null ? step.reportedAttempt().cause() : conditionCause;
            step = step.withConditionFailure(
                    cause == null ? new InterruptedException("caller thread interrupt flag was set") : cause, null);
        }
        history.add(slot, step.reportedAttempt());
        return step;
    }

    ExecutionStep<OBSERVED> interruptedBefore(Phase phase) {
        return currentThread().isInterrupted()
                ? waitingFailure(phase, new InterruptedException("caller thread interrupt flag was set"))
                : null;
    }

    ExecutionStep<OBSERVED> waitingFailure(Phase phase, Throwable failure) {
        return failed(phase,
                new FailedBeforeObservation<>(Operation.WAITING, failure));
    }

    AwaitResult<OBSERVED, RESULT> result(WaitCompletion outcome, WaitConfiguration configuration) {
        List<ConditionHistory<OBSERVED>> conditions;
        boolean interrupted = currentThread().isInterrupted();
        try {
            var definitions = condition == null ? List.of(definition)
                    : requireNonNull(condition.historyConditions(definition), "condition returned null history conditions");
            conditions = history.conditions(definitions);
        } catch (Throwable cause) {
            var step = outcome.step().withConditionFailure(cause, "condition histories");
            outcome = outcome instanceof Uncontrolled failure
                    ? new Uncontrolled(step, failure.interrupted()) : new Uncontrolled(step);
            conditions = List.of(history.flattened(definition));
        } finally {
            if (interrupted) currentThread().interrupt();
        }
        return outcome instanceof SatisfiedWait
                ? new Succeeded<>(conditions, selectedValue)
                : new Failed<>(conditions,
                        failure(definition, this::diagnostics, outcome, configuration, history.count(), conditions));
    }

    private Diagnostic diagnostics(Attempt<?> attempt) { return condition == null ? Diagnostic.EMPTY : condition.diagnostics(attempt); }
}
