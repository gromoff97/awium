package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.Attempt;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.PERSISTENCE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

public record WaitEngine<OBSERVED>(WaitConfiguration configuration, Callable<? extends OBSERVED> source,
        SequenceExecution<OBSERVED, ?> execution, long started) {

    public WaitEngine(WaitConfiguration configuration, Callable<? extends OBSERVED> source,
            SequenceExecution<OBSERVED, ?> execution) {
        this(configuration, source, execution, now(configuration));
    }

    public WaitCompletion waitFor(Consumer<ExecutionStep<OBSERVED>> recorder) {
        var runner = new AttemptExecution<>(source, execution, () -> now(configuration), started);
        WaitCompletion acquisition = acquire(recorder, runner);
        if (!(acquisition instanceof WaitCompletion.Satisfied acquired)
                || configuration.persistenceNanos() == 0) {
            return acquisition;
        }
        return persist(recorder, acquired, runner);
    }

    private WaitCompletion acquire(Consumer<ExecutionStep<OBSERVED>> recorder, AttemptExecution<OBSERVED> runner) {
        long deadline = started + configuration.upToNanos();
        ExecutionStep<OBSERVED> lastStep = null;
        long completed = started;

        while (true) {
            ExecutionStep<OBSERVED> preparationFailure = lastStep == null
                    ? runner.interruptedBefore(ACQUISITION)
                    : waitBeforeAttempt(runner, min(configuration.everyNanos(), remaining(completed, deadline)),
                            ACQUISITION, completed);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled(preparationFailure);
            }

            long retrievalStarted = now(configuration);
            if (lastStep != null && reached(retrievalStarted, deadline) && !currentThread().isInterrupted()) {
                return new WaitCompletion.Timeout(retrievalStarted - started, lastStep);
            }

            ExecutionStep<OBSERVED> step = runner.call(ACQUISITION);
            completed = started + step.completedAfterNanos();
            recorder.accept(step);
            if (step.attempt() instanceof Attempt.Failure<?>) {
                return new WaitCompletion.Uncontrolled(step);
            }
            if (reached(completed, deadline)) {
                return new WaitCompletion.Timeout(step);
            }
            if (execution.isComplete()) {
                return new WaitCompletion.Satisfied(step);
            }
            lastStep = step;
        }
    }

    private WaitCompletion persist(Consumer<ExecutionStep<OBSERVED>> recorder,
            WaitCompletion.Satisfied acquired, AttemptExecution<OBSERVED> runner) {
        long acquiredAt = started + acquired.step().completedAfterNanos();
        long deadline = acquiredAt + configuration.persistenceNanos();
        long completed = acquiredAt;

        while (true) {
            ExecutionStep<OBSERVED> preparationFailure = waitBeforeAttempt(runner,
                    min(configuration.everyNanos(), remaining(completed, deadline)), PERSISTENCE, completed);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled(preparationFailure);
            }

            // Keep the clock/cancellation boundary before source retrieval, as in acquisition.
            now(configuration);
            ExecutionStep<OBSERVED> step = runner.call(PERSISTENCE);
            completed = started + step.completedAfterNanos();
            recorder.accept(step);
            if (step.attempt() instanceof Attempt.Failure<?>) {
                return new WaitCompletion.Uncontrolled(step);
            }
            if (!execution.isComplete()) {
                return new WaitCompletion.PersistenceFailure(acquired.step().completedAfterNanos(), step);
            }
            if (reached(completed, deadline)) {
                return new WaitCompletion.Satisfied(step);
            }
        }
    }

    private ExecutionStep<OBSERVED> waitBeforeAttempt(AttemptExecution<OBSERVED> runner, long delay,
            ExecutionStep.Phase phase, long completed) {
        long deadline = completed + delay;
        long remaining;
        while ((remaining = remaining(now(configuration), deadline)) > 0) {
            ExecutionStep<OBSERVED> interrupted = runner.interruptedBefore(phase);
            if (interrupted != null) {
                return interrupted;
            }
            try {
                configuration.parker().accept(remaining);
            } catch (Throwable failure) {
                return runner.waitingFailure(phase, failure);
            }
            interrupted = runner.interruptedBefore(phase);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return runner.interruptedBefore(phase);
    }

    private static long now(WaitConfiguration configuration) {
        boolean interrupted = currentThread().isInterrupted();
        try {
            return configuration.clock().getAsLong();
        } finally {
            if (interrupted) currentThread().interrupt();
        }
    }

    private static boolean reached(long now, long deadline) {
        return now - deadline >= 0;
    }

    private static long remaining(long now, long deadline) {
        return max(deadline - now, 0);
    }
}
