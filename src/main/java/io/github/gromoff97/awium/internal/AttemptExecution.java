package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.Attempt;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.internal.ExecutionStep.Phase;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.Attempt.Operation;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static java.lang.Thread.currentThread;

record AttemptExecution<OBSERVED>(Callable<? extends OBSERVED> source,
        SequenceExecution<OBSERVED, ?> execution, LongSupplier clock, long started) {

    ExecutionStep<OBSERVED> call(Phase phase) {
        var interrupted = interruptedBefore(phase);
        if (interrupted != null) return interrupted;
        int stageNumber = execution.stageNumber();
        OBSERVED actual;
        try {
            actual = source.call();
        } catch (Throwable failure) {
            return step(phase, stageNumber,
                    new FailedBeforeObservation<>(Operation.SOURCE, failure), clock.getAsLong());
        }

        // Keep the clock/cancellation boundary before invoking user condition code.
        clock.getAsLong();
        if (currentThread().isInterrupted()) {
            return step(phase, stageNumber, new FailedAfterObservation<>(actual, Operation.SOURCE,
                    new InterruptedException("caller thread interrupt flag was set"), null), clock.getAsLong());
        }

        Attempt<OBSERVED> attempt = observe(actual);
        long completed = clock.getAsLong();
        if (!(attempt instanceof Failure<?>) && currentThread().isInterrupted()) {
            var cause = attempt.cause();
            attempt = new FailedAfterObservation<>(actual, Operation.CONDITION,
                    cause == null ? new InterruptedException("caller thread interrupt flag was set") : cause, null);
        }
        return step(phase, stageNumber, attempt, completed);
    }

    private Attempt<OBSERVED> observe(OBSERVED actual) {
        try {
            return switch (execution.probe(actual)) {
                case Satisfied<?> ignored -> new SatisfiedObservation<>(actual);
                case Unsatisfied<?> mismatch ->
                        new UnsatisfiedObservation<>(actual, mismatch.mismatch(), mismatch.cause());
            };
        } catch (ProbeFailure failure) {
            return new FailedAfterObservation<>(actual, Operation.CONDITION,
                    failure.getCause(), failure.location());
        } catch (Throwable failure) {
            return new FailedAfterObservation<>(actual, Operation.CONDITION, failure, null);
        }
    }

    private ExecutionStep<OBSERVED> step(Phase phase, int stageNumber,
            Attempt<OBSERVED> attempt, long completed) {
        return new ExecutionStep<>(execution.totalStages(), stageNumber, execution.completedStages(),
                phase, completed - started, attempt);
    }

    ExecutionStep<OBSERVED> interruptedBefore(Phase phase) {
        return currentThread().isInterrupted()
                ? waitingFailure(phase, new InterruptedException("caller thread interrupt flag was set"))
                : null;
    }

    ExecutionStep<OBSERVED> waitingFailure(Phase phase, Throwable failure) {
        return step(phase, execution.stageNumber(),
                new FailedBeforeObservation<>(Operation.WAITING, failure), clock.getAsLong());
    }

}
