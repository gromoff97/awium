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
        var runner = new AwaitRun<OBSERVED, RESULT>(source, condition, configuration.clock());
        var outcome = new WaitEngine(configuration, runner).waitFor();
        return runner.result(outcome, configuration);
    }

    private final WaitConfiguration configuration;
    private final AwaitRun<?, ?> runner;

    WaitEngine(WaitConfiguration configuration, AwaitRun<?, ?> runner) {
        this.configuration = configuration;
        this.runner = runner;
    }

    WaitCompletion waitFor() {
        var initializationFailure = runner.initialize();
        if (initializationFailure != null) {
            return new Uncontrolled(initializationFailure);
        }
        WaitCompletion acquisition = acquire();
        return switch (acquisition) {
            case SatisfiedWait acquired when configuration.persistenceNanos() > 0 -> persist(acquired);
            default -> acquisition;
        };
    }

    private WaitCompletion acquire() {
        long deadline = configuration.upToNanos();
        ExecutionStep<?> lastStep = null;

        while (true) {
            ExecutionStep<?> preparationFailure = lastStep == null
                    ? runner.interruptedBefore(ACQUISITION)
                    : waitBeforeAttempt(ACQUISITION, lastStep, deadline);
            if (preparationFailure != null) {
                return new Uncontrolled(preparationFailure);
            }

            long elapsed = runner.elapsedNanos();
            if (lastStep != null && reached(elapsed, deadline) && !currentThread().isInterrupted()) {
                return new Timeout(elapsed, lastStep);
            }

            ExecutionStep<?> step = runner.poll(ACQUISITION);
            if (step.reportedAttempt() instanceof Failure<?>) {
                return new Uncontrolled(step);
            }
            if (reached(step.completedAfterNanos(), deadline)) {
                return new Timeout(step);
            }
            if (step.conditionSatisfied()) {
                return new SatisfiedWait(step);
            }
            lastStep = step;
        }
    }

    private WaitCompletion persist(SatisfiedWait acquired) {
        ExecutionStep<?> lastStep = acquired.step();
        long deadline = lastStep.completedAfterNanos() + configuration.persistenceNanos();

        while (true) {
            ExecutionStep<?> preparationFailure = waitBeforeAttempt(PERSISTENCE, lastStep, deadline);
            if (preparationFailure != null) {
                return new Uncontrolled(preparationFailure);
            }

            ExecutionStep<?> step = runner.poll(PERSISTENCE);
            if (step.reportedAttempt() instanceof Failure<?>) {
                return new Uncontrolled(step);
            }
            if (!step.conditionSatisfied()) {
                return new PersistenceFailure(acquired.step().completedAfterNanos(), step);
            }
            if (reached(step.completedAfterNanos(), deadline)) {
                return new SatisfiedWait(step);
            }
            lastStep = step;
        }
    }

    private ExecutionStep<?> waitBeforeAttempt(Phase phase, ExecutionStep<?> previous, long waitDeadline) {
        long completed = previous.completedAfterNanos();
        long deadline = completed + min(configuration.everyNanos(), remaining(completed, waitDeadline));
        long remaining;
        while ((remaining = remaining(runner.elapsedNanos(), deadline)) > 0) {
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
