package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.execution.ProbeFailure;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.internal.ExecutionStep.Phase;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.Attempt.Operation;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.Unsatisfied;
import static java.lang.Thread.currentThread;

record AttemptExecution<OBSERVED>(Callable<? extends OBSERVED> source,
        ConditionRun<OBSERVED, ?> execution, LongSupplier clock, long started) {

    ExecutionStep<OBSERVED> initialize() {
        try {
            execution.initialize();
            return null;
        } catch (Throwable failure) {
            return failed(Phase.ACQUISITION,
                    new FailedBeforeObservation<>(Operation.CONDITION, failure));
        }
    }

    ExecutionStep<OBSERVED> call(Phase phase) {
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
        try {
            var checked = execution.probe(actual);
            diagnostic = checked.diagnostic();
            conditionCause = checked.cause();
            var recorded = execution.historyResult();
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
        return finish(phase, attempt, conditionCause);
    }

    private ExecutionStep<OBSERVED> failed(Phase phase, Failure<OBSERVED> attempt) {
        return finish(phase, attempt, null);
    }

    private ExecutionStep<OBSERVED> finish(Phase phase, Attempt<OBSERVED> attempt, Throwable conditionCause) {
        Throwable routingFailure = null;
        int slot = 1;
        boolean interrupted = currentThread().isInterrupted();
        try {
            slot = execution.historySlot(attempt);
        } catch (Throwable failure) {
            routingFailure = failure;
        } finally {
            if (interrupted) currentThread().interrupt();
        }
        var step = new ExecutionStep<>(phase, clock.getAsLong() - started, attempt)
                .withRelatedCause(conditionCause);
        if (routingFailure != null) step = step.withConditionFailure(routingFailure, "condition history");
        if (!(step.attempt() instanceof Failure<?>) && currentThread().isInterrupted()) {
            var cause = conditionCause == null ? step.attempt().cause() : conditionCause;
            step = step.withConditionFailure(
                    cause == null ? new InterruptedException("caller thread interrupt flag was set") : cause, null);
        }
        execution.record(slot, step.attempt());
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

}
