package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.Attempt;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

import static java.lang.Thread.currentThread;

/** Retrieves observations, invokes their bound attempts, and records execution diagnostics. */
record AttemptExecution<OBSERVED, RESULT>(Callable<? extends OBSERVED> source,
        SequenceExecution<OBSERVED, RESULT> execution, LongSupplier clock, long started) {

    ExecutionStep<OBSERVED> call(ExecutionStep.Phase phase) {
        var interrupted = interruptedBefore(phase);
        if (interrupted != null) return interrupted;
        int stageNumber = execution.stageNumber();
        OBSERVED actual;
        try {
            actual = source.call();
        } catch (Throwable failure) {
            return step(phase, stageNumber,
                    new Attempt.FailedBeforeObservation<>(Attempt.Operation.SOURCE, failure), clock.getAsLong());
        }

        // Keep the clock/cancellation boundary before invoking user condition code.
        clock.getAsLong();
        if (currentThread().isInterrupted()) {
            return step(phase, stageNumber, new Attempt.FailedAfterObservation<>(actual, Attempt.Operation.SOURCE,
                    new InterruptedException("caller thread interrupt flag was set"), null), clock.getAsLong());
        }

        var evaluation = execution.attempt(actual).call();
        execution.record(evaluation);
        long completed = clock.getAsLong();
        if (!(evaluation instanceof AttemptResult.Uncontrolled<?>) && currentThread().isInterrupted()) {
            var cause = evaluation.cause();
            evaluation = AttemptResult.uncontrolled(cause == null
                    ? new InterruptedException("caller thread interrupt flag was set") : cause);
        }
        Attempt<OBSERVED> attempt = switch (evaluation) {
            case AttemptResult.Satisfied<?> ignored -> new Attempt.Satisfied<>(actual);
            case AttemptResult.Unsatisfied<?> mismatch ->
                    new Attempt.Unsatisfied<>(actual, mismatch.mismatch(), mismatch.cause());
            case AttemptResult.Uncontrolled<?> failure -> new Attempt.FailedAfterObservation<>(actual,
                    Attempt.Operation.CONDITION, failure.cause(), failure.location());
        };
        return step(phase, stageNumber, attempt, completed);
    }

    private ExecutionStep<OBSERVED> step(ExecutionStep.Phase phase, int stageNumber,
            Attempt<OBSERVED> attempt, long completed) {
        return new ExecutionStep<>(execution.definition(), stageNumber, execution.capturedStages(),
                phase, completed - started, attempt);
    }

    ExecutionStep<OBSERVED> interruptedBefore(ExecutionStep.Phase phase) {
        return currentThread().isInterrupted()
                ? waitingFailure(phase, new InterruptedException("caller thread interrupt flag was set"))
                : null;
    }

    ExecutionStep<OBSERVED> waitingFailure(ExecutionStep.Phase phase, Throwable failure) {
        return step(phase, execution.stageNumber(),
                new Attempt.FailedBeforeObservation<>(Attempt.Operation.WAITING, failure), clock.getAsLong());
    }

}
