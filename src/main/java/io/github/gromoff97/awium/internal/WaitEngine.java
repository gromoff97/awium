package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.HistoryEntry;
import io.github.gromoff97.awium.result.AttemptStatus;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static io.github.gromoff97.awium.result.HistoryEntry.Phase.ACQUISITION;
import static io.github.gromoff97.awium.result.HistoryEntry.Phase.PERSISTENCE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

/** Schedules attempts through acquisition and optional persistence. */
public record WaitEngine<OBSERVED>(WaitConfiguration configuration, Callable<? extends OBSERVED> source,
        SequenceExecution<OBSERVED, ?> execution, long started) {

    public WaitEngine(WaitConfiguration configuration, Callable<? extends OBSERVED> source,
            SequenceExecution<OBSERVED, ?> execution) {
        this(configuration, source, execution, now(configuration));
    }

    public WaitCompletion waitFor() {
        return waitFor(ignored -> {});
    }

    public WaitCompletion waitFor(Consumer<HistoryEntry<OBSERVED>> recorder) {
        var runner = new AttemptExecution<>(source, execution, () -> now(configuration), started);
        WaitCompletion acquisition = acquire(recorder, runner);
        if (!(acquisition instanceof WaitCompletion.Satisfied acquired)
                || configuration.persistenceNanos() == 0) {
            return acquisition;
        }
        return persist(recorder, acquired, runner);
    }

    private WaitCompletion acquire(Consumer<HistoryEntry<OBSERVED>> recorder, AttemptExecution<OBSERVED, ?> runner) {
        long deadline = started + configuration.upToNanos();
        HistoryEntry<OBSERVED> lastAttempt = null;
        long completed = started;

        while (true) {
            long attemptStarted = completed;
            int stageNumber = execution.stageNumber();
            HistoryEntry<OBSERVED> preparationFailure = lastAttempt == null
                    ? runner.interruptedBefore(ACQUISITION, attemptStarted, stageNumber)
                    : waitBeforeAttempt(runner, min(configuration.everyNanos(), remaining(completed, deadline)),
                            ACQUISITION, attemptStarted, stageNumber);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled(preparationFailure);
            }

            long retrievalStarted = now(configuration);
            if (lastAttempt != null && reached(retrievalStarted, deadline) && !currentThread().isInterrupted()) {
                return new WaitCompletion.Timeout(runner.offset(retrievalStarted), lastAttempt);
            }

            HistoryEntry<OBSERVED> attempt = runner.call(ACQUISITION,
                    attemptStarted, retrievalStarted, stageNumber);
            completed = runner.completedNanos(attempt);
            recorder.accept(attempt);
            if (attempt.result() instanceof AttemptStatus.Uncontrolled) {
                return new WaitCompletion.Uncontrolled(attempt);
            }
            if (reached(completed, deadline)) {
                return new WaitCompletion.Timeout(attempt);
            }
            if (execution.complete()) {
                return new WaitCompletion.Satisfied(attempt);
            }
            lastAttempt = attempt;
        }
    }

    private WaitCompletion persist(Consumer<HistoryEntry<OBSERVED>> recorder,
            WaitCompletion.Satisfied acquired, AttemptExecution<OBSERVED, ?> runner) {
        long acquiredAt = runner.completedNanos(acquired.attempt());
        long deadline = acquiredAt + configuration.persistenceNanos();
        long completed = acquiredAt;

        while (true) {
            int stageNumber = execution.stageNumber();
            long attemptStarted = completed;
            long delay = min(configuration.everyNanos(), remaining(completed, deadline));
            HistoryEntry<OBSERVED> preparationFailure = waitBeforeAttempt(runner, delay,
                    PERSISTENCE, attemptStarted, stageNumber);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled(preparationFailure);
            }

            long retrievalStarted = now(configuration);
            HistoryEntry<OBSERVED> attempt = runner.call(PERSISTENCE,
                    attemptStarted, retrievalStarted, stageNumber);
            completed = runner.completedNanos(attempt);
            recorder.accept(attempt);
            if (attempt.result() instanceof AttemptStatus.Uncontrolled) {
                return new WaitCompletion.Uncontrolled(attempt);
            }
            if (!execution.complete()) {
                return new WaitCompletion.PersistenceFailure(acquired.attempt().timing().completionOffset(), attempt);
            }
            if (reached(completed, deadline)) {
                return new WaitCompletion.Satisfied(attempt);
            }
        }
    }

    private HistoryEntry<OBSERVED> waitBeforeAttempt(AttemptExecution<OBSERVED, ?> runner, long delay,
            HistoryEntry.Phase phase,
            long attemptStarted, int stageNumber) {
        long deadline = attemptStarted + delay;
        long remaining;
        while ((remaining = remaining(now(configuration), deadline)) > 0) {
            HistoryEntry<OBSERVED> interrupted = runner.interruptedBefore(phase, attemptStarted, stageNumber);
            if (interrupted != null) {
                return interrupted;
            }
            try {
                configuration.parker().accept(remaining);
            } catch (Throwable failure) {
                return runner.waitingFailure(phase, attemptStarted, stageNumber, failure);
            }
            interrupted = runner.interruptedBefore(phase, attemptStarted, stageNumber);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return runner.interruptedBefore(phase, attemptStarted, stageNumber);
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
