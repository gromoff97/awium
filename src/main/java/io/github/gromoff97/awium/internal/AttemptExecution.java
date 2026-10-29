package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.HistoryEntry;
import io.github.gromoff97.awium.result.AttemptStatus;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

import static java.lang.Thread.currentThread;

/** Retrieves observations, invokes their bound attempts, and records execution diagnostics. */
final class AttemptExecution<OBSERVED, RESULT> {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final Callable<? extends OBSERVED> source;
    private final SequenceExecution<OBSERVED, RESULT> execution;
    private final LongSupplier clock;
    private final long started;

    AttemptExecution(Callable<? extends OBSERVED> source, SequenceExecution<OBSERVED, RESULT> execution,
            LongSupplier clock, long started) {
        this.source = source;
        this.execution = execution;
        this.clock = clock;
        this.started = started;
    }

    HistoryEntry<OBSERVED> call(HistoryEntry.Phase phase,
            long attemptStarted, long retrievalStarted, int stageNumber) {
        HistoryEntry<OBSERVED> interrupted = interruptedBefore(phase, attemptStarted, stageNumber);
        if (interrupted != null) {
            return interrupted;
        }
        OBSERVED actual;
        try {
            actual = source.call();
        } catch (Throwable failure) {
            long completed = clock.getAsLong();
            var timing = new HistoryEntry.Timing(offset(attemptStarted),
                    offset(retrievalStarted), null, offset(completed));
            return new HistoryEntry<>(phase, timing, execution.condition(stageNumber), execution.progress(stageNumber),
                    null, new AttemptStatus.Uncontrolled(AttemptStatus.Operation.SOURCE, failure));
        }

        long observed = clock.getAsLong();
        if (currentThread().isInterrupted()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            long completed = clock.getAsLong();
            var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
            return new HistoryEntry<>(phase, timing, execution.condition(stageNumber), execution.progress(stageNumber),
                    actual, new AttemptStatus.Uncontrolled(AttemptStatus.Operation.SOURCE, interruption));
        }

        var attempt = execution.attempt(actual);
        var evaluation = attempt.call();
        execution.record(evaluation);
        var result = evaluation.status();
        long completed = clock.getAsLong();

        if (!(result instanceof AttemptStatus.Uncontrolled) && currentThread().isInterrupted()) {
            var cause = result.cause();
            result = new AttemptStatus.Uncontrolled(AttemptStatus.Operation.CONDITION,
                    cause == null ? new InterruptedException("caller thread interrupt flag was set") : cause);
        }
        var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
        return new HistoryEntry<>(phase, timing, attempt.condition(),
                execution.progress(stageNumber), actual, result);
    }

    private HistoryEntry.Timing afterObservation(long attemptStarted, long retrievalStarted,
            long observed, long completed) {
        return new HistoryEntry.Timing(offset(attemptStarted),
                offset(retrievalStarted),
                offset(observed),
                offset(completed));
    }

    HistoryEntry<OBSERVED> interruptedBefore(HistoryEntry.Phase phase,
            long attemptStarted, int stageNumber) {
        if (!currentThread().isInterrupted()) {
            return null;
        }
        return waitingFailure(phase, attemptStarted, stageNumber,
                new InterruptedException("caller thread interrupt flag was set"));
    }

    HistoryEntry<OBSERVED> waitingFailure(HistoryEntry.Phase phase,
            long attemptStarted, int stageNumber, Throwable failure) {
        long completed = clock.getAsLong();
        var timing = new HistoryEntry.Timing(offset(attemptStarted), null, null, offset(completed));
        return new HistoryEntry<>(phase, timing, execution.condition(stageNumber), execution.progress(stageNumber),
                null, new AttemptStatus.Uncontrolled(AttemptStatus.Operation.WAITING, failure));
    }

    long completedNanos(HistoryEntry<?> attempt) {
        var elapsed = attempt.timing().completionOffset();
        // Reconstruct the clock reading with the same wrapping arithmetic as nanoTime.
        return started + elapsed.getSeconds() * NANOS_PER_SECOND + elapsed.getNano();
    }

    Duration offset(long stage) {
        // Acquisition and persistence each fit in signed nanos; their sum need not.
        long elapsed = stage - started;
        return Duration.ofSeconds(Long.divideUnsigned(elapsed, NANOS_PER_SECOND),
                Long.remainderUnsigned(elapsed, NANOS_PER_SECOND));
    }
}
