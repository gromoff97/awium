package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.execution.ExecutionStep;
import io.github.gromoff97.awium.execution.ProbeFailure;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.execution.ExecutionStep.Phase;
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

    ExecutionStep<OBSERVED> call(Phase phase) {
        var interrupted = interruptedBefore(phase);
        if (interrupted != null) return interrupted;
        execution.beginAttempt();
        if (currentThread().isInterrupted()) {
            return failed(phase, new FailedBeforeObservation<>(Operation.CONDITION,
                    new InterruptedException("caller thread interrupt flag was set")), clock.getAsLong());
        }
        OBSERVED actual;
        try {
            actual = source.call();
        } catch (Throwable failure) {
            return failed(phase,
                    new FailedBeforeObservation<>(Operation.SOURCE, failure), clock.getAsLong());
        }

        if (currentThread().isInterrupted()) {
            return failed(phase, new FailedAfterObservation<>(actual, Operation.SOURCE,
                    new InterruptedException("caller thread interrupt flag was set"), null), clock.getAsLong());
        }

        Attempt<OBSERVED> attempt;
        Diagnostic diagnostic = Diagnostic.EMPTY;
        boolean complete = false;
        try {
            var checked = execution.probe(actual);
            diagnostic = checked.diagnostic();
            attempt = switch (checked) {
                case Satisfied<?> success -> new SatisfiedObservation<>(actual, success.diagnostic());
                case Unsatisfied<?> mismatch ->
                        new UnsatisfiedObservation<>(actual, mismatch.diagnostic(), mismatch.cause());
            };
            if (!currentThread().isInterrupted()) complete = execution.complete();
        } catch (ProbeFailure failure) {
            attempt = new FailedAfterObservation<>(actual, Operation.CONDITION,
                    failure.getCause(), failure.location(), diagnostic);
        } catch (Throwable failure) {
            attempt = new FailedAfterObservation<>(actual, Operation.CONDITION, failure, null, diagnostic);
        }
        long completed = clock.getAsLong();
        if (!(attempt instanceof Failure<?>) && currentThread().isInterrupted()) {
            var cause = attempt.cause();
            attempt = new FailedAfterObservation<>(actual, Operation.CONDITION,
                    cause == null ? new InterruptedException("caller thread interrupt flag was set") : cause, null, attempt.diagnostic());
            complete = false;
        }
        return new ExecutionStep<>(complete, phase, completed - started, attempt);
    }

    private ExecutionStep<OBSERVED> failed(Phase phase,
            Failure<OBSERVED> attempt, long completed) {
        return new ExecutionStep<>(false,
                phase, completed - started, attempt);
    }

    ExecutionStep<OBSERVED> interruptedBefore(Phase phase) {
        return currentThread().isInterrupted()
                ? waitingFailure(phase, new InterruptedException("caller thread interrupt flag was set"))
                : null;
    }

    ExecutionStep<OBSERVED> waitingFailure(Phase phase, Throwable failure) {
        execution.beginAttempt();
        return failed(phase,
                new FailedBeforeObservation<>(Operation.WAITING, failure), clock.getAsLong());
    }

}
