package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.execution.ExecutionStep;
import io.github.gromoff97.awium.execution.WaitCompletion;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static io.github.gromoff97.awium.execution.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.execution.ExecutionStep.Phase.PERSISTENCE;
import static io.github.gromoff97.awium.execution.ExecutionStep.Phase;
import static io.github.gromoff97.awium.execution.WaitCompletion.PersistenceFailure;
import static io.github.gromoff97.awium.execution.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.execution.WaitCompletion.Timeout;
import static io.github.gromoff97.awium.execution.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

public final class WaitEngine<OBSERVED> {

    private final WaitConfiguration configuration;
    private final AttemptExecution<OBSERVED> runner;
    private final long started;

    public WaitEngine(WaitConfiguration configuration, Callable<? extends OBSERVED> source,
            ConditionRun<OBSERVED, ?> execution) {
        this.configuration = configuration;
        started = configuration.clock().getAsLong();
        runner = new AttemptExecution<>(source, execution, configuration.clock(), started);
    }

    public WaitCompletion waitFor(Consumer<ExecutionStep<OBSERVED>> recorder) {
        WaitCompletion acquisition = acquire(recorder);
        if (!(acquisition instanceof SatisfiedWait acquired)
                || configuration.persistenceNanos() == 0) {
            return acquisition;
        }
        return persist(recorder, acquired);
    }

    private WaitCompletion acquire(Consumer<ExecutionStep<OBSERVED>> recorder) {
        long deadline = started + configuration.upToNanos();
        ExecutionStep<OBSERVED> lastStep = null;
        long completed = started;

        while (true) {
            ExecutionStep<OBSERVED> preparationFailure = lastStep == null
                    ? runner.interruptedBefore(ACQUISITION)
                    : waitBeforeAttempt(min(configuration.everyNanos(), remaining(completed, deadline)),
                            ACQUISITION, completed);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new Uncontrolled(preparationFailure);
            }

            long retrievalStarted = configuration.clock().getAsLong();
            if (lastStep != null && reached(retrievalStarted, deadline) && !currentThread().isInterrupted()) {
                return new Timeout(retrievalStarted - started, lastStep);
            }

            ExecutionStep<OBSERVED> step = runner.call(ACQUISITION);
            completed = started + step.completedAfterNanos();
            recorder.accept(step);
            if (step.attempt() instanceof Failure<?>) {
                return new Uncontrolled(step);
            }
            if (reached(completed, deadline)) {
                return new Timeout(step);
            }
            if (step.complete()) {
                return new SatisfiedWait(step);
            }
            lastStep = step;
        }
    }

    private WaitCompletion persist(Consumer<ExecutionStep<OBSERVED>> recorder,
            SatisfiedWait acquired) {
        long acquiredAt = started + acquired.step().completedAfterNanos();
        long deadline = acquiredAt + configuration.persistenceNanos();
        long completed = acquiredAt;

        while (true) {
            ExecutionStep<OBSERVED> preparationFailure = waitBeforeAttempt(
                    min(configuration.everyNanos(), remaining(completed, deadline)), PERSISTENCE, completed);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new Uncontrolled(preparationFailure);
            }

            // Keep the clock/cancellation boundary before source retrieval, as in acquisition.
            configuration.clock().getAsLong();
            ExecutionStep<OBSERVED> step = runner.call(PERSISTENCE);
            completed = started + step.completedAfterNanos();
            recorder.accept(step);
            if (step.attempt() instanceof Failure<?>) {
                return new Uncontrolled(step);
            }
            if (!step.complete()) {
                return new PersistenceFailure(acquired.step().completedAfterNanos(), step);
            }
            if (reached(completed, deadline)) {
                return new SatisfiedWait(step);
            }
        }
    }

    private ExecutionStep<OBSERVED> waitBeforeAttempt(long delay,
            Phase phase, long completed) {
        long deadline = completed + delay;
        long remaining;
        while ((remaining = remaining(configuration.clock().getAsLong(), deadline)) > 0) {
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

    private static boolean reached(long now, long deadline) {
        return now - deadline >= 0;
    }

    private static long remaining(long now, long deadline) {
        return max(deadline - now, 0);
    }
}
