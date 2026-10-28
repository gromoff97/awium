package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.AttemptRecord;
import io.github.gromoff97.awium.result.AttemptResult;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static io.github.gromoff97.awium.result.AttemptRecord.Phase.ACQUISITION;
import static io.github.gromoff97.awium.result.AttemptRecord.Phase.PERSISTENCE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

/** One wait execution, including its observations, timing, and condition attempt. */
public record WaitEngine<OBSERVED>(WaitConfiguration configuration, Callable<? extends OBSERVED> source,
        SequenceExecution<OBSERVED, ?> execution, long started) {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    public WaitEngine(WaitConfiguration configuration, Callable<? extends OBSERVED> source,
            SequenceExecution<OBSERVED, ?> execution) {
        this(configuration, source, execution, now(configuration));
    }

    public WaitCompletion waitFor() {
        return waitFor(ignored -> {});
    }

    public WaitCompletion waitFor(Consumer<AttemptRecord<OBSERVED>> recorder) {
        WaitCompletion acquisition = acquire(recorder);
        if (!(acquisition instanceof WaitCompletion.Satisfied acquired)
                || configuration.persistenceNanos() == 0) {
            return acquisition;
        }
        return persist(recorder, acquired);
    }

    private WaitCompletion acquire(Consumer<AttemptRecord<OBSERVED>> recorder) {
        long deadline = started + configuration.upToNanos();
        AttemptRecord<OBSERVED> lastAttempt = null;
        long completed = started;

        for (long number = 1;; number++) {
            long attemptStarted = completed;
            int stageNumber = execution.stageNumber();
            AttemptRecord<OBSERVED> preparationFailure = number == 1
                    ? interruptedBefore(ACQUISITION, number, attemptStarted, stageNumber)
                    : waitBeforeAttempt(min(configuration.everyNanos(), remaining(completed, deadline)),
                            ACQUISITION, number, attemptStarted, stageNumber);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled(preparationFailure);
            }

            long retrievalStarted = now(configuration);
            if (number > 1 && reached(retrievalStarted, deadline) && !currentThread().isInterrupted()) {
                return new WaitCompletion.Timeout(offset(retrievalStarted), lastAttempt);
            }

            AttemptRecord<OBSERVED> attempt = evaluate(ACQUISITION,
                    number, attemptStarted, retrievalStarted, stageNumber);
            completed = completedNanos(attempt);
            recorder.accept(attempt);
            if (attempt.result() instanceof AttemptResult.Uncontrolled) {
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

    private WaitCompletion persist(Consumer<AttemptRecord<OBSERVED>> recorder,
            WaitCompletion.Satisfied acquired) {
        long acquiredAt = completedNanos(acquired.attempt());
        long deadline = acquiredAt + configuration.persistenceNanos();
        long completed = acquiredAt;

        for (long number = acquired.attempt().number() + 1;; number++) {
            int stageNumber = execution.stageNumber();
            long attemptStarted = completed;
            long delay = min(configuration.everyNanos(), remaining(completed, deadline));
            AttemptRecord<OBSERVED> preparationFailure = waitBeforeAttempt(delay,
                    PERSISTENCE, number, attemptStarted, stageNumber);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled(preparationFailure);
            }

            long retrievalStarted = now(configuration);
            AttemptRecord<OBSERVED> attempt = evaluate(PERSISTENCE,
                    number, attemptStarted, retrievalStarted, stageNumber);
            completed = completedNanos(attempt);
            recorder.accept(attempt);
            if (attempt.result() instanceof AttemptResult.Uncontrolled) {
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

    private AttemptRecord<OBSERVED> evaluate(AttemptRecord.Phase phase, long number,
            long attemptStarted, long retrievalStarted, int stageNumber) {
        AttemptRecord<OBSERVED> interrupted = interruptedBefore(phase, number, attemptStarted, stageNumber);
        if (interrupted != null) {
            return interrupted;
        }
        OBSERVED actual;
        try {
            actual = source.call();
        } catch (Throwable failure) {
            long completed = now(configuration);
            var timing = new AttemptRecord.Timing(offset(attemptStarted),
                    offset(retrievalStarted), null, offset(completed));
            return new AttemptRecord<>(number, phase, timing, execution.condition(stageNumber), execution.progress(stageNumber),
                    null, new AttemptResult.Uncontrolled(AttemptResult.Operation.SOURCE, failure));
        }

        long observed = now(configuration);
        if (currentThread().isInterrupted()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            long completed = now(configuration);
            var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
            return new AttemptRecord<>(number, phase, timing, execution.condition(stageNumber), execution.progress(stageNumber),
                    actual, new AttemptResult.Uncontrolled(AttemptResult.Operation.SOURCE, interruption));
        }

        var result = execution.step(actual);
        long completed = now(configuration);

        if (!(result instanceof AttemptResult.Uncontrolled) && currentThread().isInterrupted()) {
            var cause = result.cause();
            result = new AttemptResult.Uncontrolled(AttemptResult.Operation.CONDITION,
                    cause == null ? new InterruptedException("caller thread interrupt flag was set") : cause);
        }
        var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
        return new AttemptRecord<>(number, phase, timing, execution.checkedCondition(),
                execution.progress(stageNumber), actual, result);
    }

    private AttemptRecord.Timing afterObservation(long attemptStarted, long retrievalStarted,
            long observed, long completed) {
        return new AttemptRecord.Timing(offset(attemptStarted),
                offset(retrievalStarted),
                offset(observed),
                offset(completed));
    }

    private AttemptRecord<OBSERVED> waitBeforeAttempt(long delay,
            AttemptRecord.Phase phase, long number,
            long attemptStarted, int stageNumber) {
        long deadline = attemptStarted + delay;
        long remaining;
        while ((remaining = remaining(now(configuration), deadline)) > 0) {
            AttemptRecord<OBSERVED> interrupted = interruptedBefore(phase, number, attemptStarted, stageNumber);
            if (interrupted != null) {
                return interrupted;
            }
            try {
                configuration.parker().accept(remaining);
            } catch (Throwable failure) {
                return waitingFailure(phase, number, attemptStarted, stageNumber, failure);
            }
            interrupted = interruptedBefore(phase, number, attemptStarted, stageNumber);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return interruptedBefore(phase, number, attemptStarted, stageNumber);
    }

    private AttemptRecord<OBSERVED> interruptedBefore(AttemptRecord.Phase phase, long number,
            long attemptStarted, int stageNumber) {
        if (!currentThread().isInterrupted()) {
            return null;
        }
        return waitingFailure(phase, number, attemptStarted, stageNumber,
                new InterruptedException("caller thread interrupt flag was set"));
    }

    private AttemptRecord<OBSERVED> waitingFailure(AttemptRecord.Phase phase, long number,
            long attemptStarted, int stageNumber, Throwable failure) {
        long completed = now(configuration);
        var timing = new AttemptRecord.Timing(offset(attemptStarted), null, null, offset(completed));
        return new AttemptRecord<>(number, phase, timing, execution.condition(stageNumber), execution.progress(stageNumber),
                null, new AttemptResult.Uncontrolled(AttemptResult.Operation.WAITING, failure));
    }

    private long completedNanos(AttemptRecord<?> attempt) {
        var elapsed = attempt.timing().completionOffset();
        // Reconstruct the clock reading with the same wrapping arithmetic as nanoTime.
        return started + elapsed.getSeconds() * NANOS_PER_SECOND + elapsed.getNano();
    }

    private Duration offset(long stage) {
        // Acquisition and persistence each fit in signed nanos; their sum need not.
        long elapsed = stage - started;
        return Duration.ofSeconds(Long.divideUnsigned(elapsed, NANOS_PER_SECOND),
                Long.remainderUnsigned(elapsed, NANOS_PER_SECOND));
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
