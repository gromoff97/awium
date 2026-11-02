package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.PERSISTENCE;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase;
import static io.github.gromoff97.awium.internal.WaitCompletion.PersistenceFailure;
import static io.github.gromoff97.awium.internal.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.internal.WaitCompletion.Timeout;
import static io.github.gromoff97.awium.internal.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

public final class WaitEngine {

    public static <OBSERVED, RESULT> AwaitResult<OBSERVED, RESULT> execute(
            WaitConfiguration configuration, Callable<? extends OBSERVED> source, AwaitCondition condition) {
        var execution = new ConditionRun<OBSERVED, RESULT>(condition);
        var outcome = new WaitEngine(configuration, source, execution).waitFor();
        return execution.finish(outcome, configuration);
    }

    private final WaitConfiguration configuration;
    private final AttemptExecution<?> runner;
    private final long started;

    <OBSERVED> WaitEngine(WaitConfiguration configuration, Callable<? extends OBSERVED> source,
            ConditionRun<OBSERVED, ?> execution) {
        this.configuration = configuration;
        started = configuration.clock().getAsLong();
        runner = new AttemptExecution<>(source, execution, configuration.clock(), started);
    }

    WaitCompletion waitFor() {
        var initializationFailure = runner.initialize();
        if (initializationFailure != null) {
            return new Uncontrolled(initializationFailure);
        }
        WaitCompletion acquisition = acquire();
        if (!(acquisition instanceof SatisfiedWait acquired)
                || configuration.persistenceNanos() == 0) {
            return acquisition;
        }
        return persist(acquired);
    }

    private WaitCompletion acquire() {
        long deadline = started + configuration.upToNanos();
        ExecutionStep<?> lastStep = null;
        long completed = started;

        while (true) {
            ExecutionStep<?> preparationFailure = lastStep == null
                    ? runner.interruptedBefore(ACQUISITION)
                    : waitBeforeAttempt(min(configuration.everyNanos(), remaining(completed, deadline)),
                            ACQUISITION, completed);
            if (preparationFailure != null) {
                return new Uncontrolled(preparationFailure);
            }

            long retrievalStarted = configuration.clock().getAsLong();
            if (lastStep != null && reached(retrievalStarted, deadline) && !currentThread().isInterrupted()) {
                return new Timeout(retrievalStarted - started, lastStep);
            }

            ExecutionStep<?> step = runner.call(ACQUISITION);
            completed = started + step.completedAfterNanos();
            if (step.attempt() instanceof Failure<?>) {
                return new Uncontrolled(step);
            }
            if (reached(completed, deadline)) {
                return new Timeout(step);
            }
            if (runner.execution().isSatisfied()) {
                return new SatisfiedWait(step);
            }
            lastStep = step;
        }
    }

    private WaitCompletion persist(SatisfiedWait acquired) {
        long acquiredAt = started + acquired.step().completedAfterNanos();
        long deadline = acquiredAt + configuration.persistenceNanos();
        long completed = acquiredAt;

        while (true) {
            ExecutionStep<?> preparationFailure = waitBeforeAttempt(
                    min(configuration.everyNanos(), remaining(completed, deadline)), PERSISTENCE, completed);
            if (preparationFailure != null) {
                return new Uncontrolled(preparationFailure);
            }

            ExecutionStep<?> step = runner.call(PERSISTENCE);
            completed = started + step.completedAfterNanos();
            if (step.attempt() instanceof Failure<?>) {
                return new Uncontrolled(step);
            }
            if (!runner.execution().isSatisfied()) {
                return new PersistenceFailure(acquired.step().completedAfterNanos(), step);
            }
            if (reached(completed, deadline)) {
                return new SatisfiedWait(step);
            }
        }
    }

    private ExecutionStep<?> waitBeforeAttempt(long delay,
            Phase phase, long completed) {
        long deadline = completed + delay;
        long remaining;
        while ((remaining = remaining(configuration.clock().getAsLong(), deadline)) > 0) {
            ExecutionStep<?> interrupted = runner.interruptedBefore(phase);
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
