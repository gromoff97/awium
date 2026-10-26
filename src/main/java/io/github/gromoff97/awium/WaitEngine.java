package io.github.gromoff97.awium;

import io.github.gromoff97.awium.AttemptRecord.Outcome.Evaluated;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static io.github.gromoff97.awium.AttemptRecord.Phase.ACQUISITION;
import static io.github.gromoff97.awium.AttemptRecord.Phase.PERSISTENCE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

/** One wait execution, including its observations, timing, and condition attempt. */
record WaitEngine<OBSERVED, RESULT>(WaitConfiguration configuration, Callable<? extends OBSERVED> source,
        ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator, long started) {

    WaitEngine(WaitConfiguration configuration, Callable<? extends OBSERVED> source,
            ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator) {
        this(configuration, source, evaluator, now(configuration));
    }

    WaitCompletion<OBSERVED, RESULT> waitFor() {
        return waitFor(ignored -> {});
    }

    WaitCompletion<OBSERVED, RESULT> waitFor(Consumer<AttemptRecord<OBSERVED, RESULT>> recorder) {
        WaitCompletion<OBSERVED, RESULT> acquisition = acquire(recorder);
        if (!(acquisition instanceof WaitCompletion.Satisfied<OBSERVED, RESULT> acquired)
                || configuration.persistenceNanos() == 0) {
            return acquisition;
        }
        return persist(recorder, acquired);
    }

    private WaitCompletion<OBSERVED, RESULT> acquire(Consumer<AttemptRecord<OBSERVED, RESULT>> recorder) {
        long deadline = started + configuration.upToNanos();
        AttemptRecord<OBSERVED, RESULT> lastUnsatisfied = null;
        long completed = started;

        for (long number = 1;; number++) {
            long attemptStarted = completed;
            var progress = lastUnsatisfied == null ? null : lastUnsatisfied.progress();
            AttemptRecord<OBSERVED, RESULT> preparationFailure = number == 1
                    ? interruptedBefore(ACQUISITION, number, attemptStarted, progress)
                    : waitBeforeAttempt(min(configuration.everyNanos(), remaining(completed, deadline)),
                            ACQUISITION, number, attemptStarted, progress);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled<>(preparationFailure);
            }

            long retrievalStarted = now(configuration);
            if (number > 1 && reached(retrievalStarted, deadline) && !currentThread().isInterrupted()) {
                return new WaitCompletion.Timeout<>(retrievalStarted - started, lastUnsatisfied);
            }

            AttemptRecord<OBSERVED, RESULT> attempt = evaluate(ACQUISITION,
                    number, attemptStarted, retrievalStarted, progress);
            completed = completedNanos(attempt);
            recorder.accept(attempt);
            if (!(attempt.outcome() instanceof Evaluated<OBSERVED, RESULT> evaluated)) {
                return new WaitCompletion.Uncontrolled<>(attempt);
            }
            if (reached(completed, deadline)) {
                return new WaitCompletion.Timeout<>(attempt);
            }
            if (evaluated.evaluation() instanceof ConditionResult.Satisfied<?>) {
                return new WaitCompletion.Satisfied<>(attempt);
            }
            lastUnsatisfied = attempt;
        }
    }

    private WaitCompletion<OBSERVED, RESULT> persist(Consumer<AttemptRecord<OBSERVED, RESULT>> recorder,
            WaitCompletion.Satisfied<OBSERVED, RESULT> acquired) {
        long acquiredAt = completedNanos(acquired.attempt());
        long deadline = acquiredAt + configuration.persistenceNanos();
        long completed = acquiredAt;
        var progress = acquired.attempt().progress();

        for (long number = acquired.attempt().number() + 1;; number++) {
            long attemptStarted = completed;
            long delay = min(configuration.everyNanos(), remaining(completed, deadline));
            AttemptRecord<OBSERVED, RESULT> preparationFailure = waitBeforeAttempt(delay,
                    PERSISTENCE, number, attemptStarted, progress);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled<>(preparationFailure);
            }

            long retrievalStarted = now(configuration);
            AttemptRecord<OBSERVED, RESULT> attempt = evaluate(PERSISTENCE,
                    number, attemptStarted, retrievalStarted, progress);
            completed = completedNanos(attempt);
            progress = attempt.progress();
            recorder.accept(attempt);
            if (!(attempt.outcome() instanceof Evaluated<OBSERVED, RESULT> evaluated)) {
                return new WaitCompletion.Uncontrolled<>(attempt);
            }
            if (!(evaluated.evaluation() instanceof ConditionResult.Satisfied<?>)) {
                return new WaitCompletion.PersistenceFailure<>(acquiredAt - started, attempt);
            }
            if (reached(completed, deadline)) {
                return new WaitCompletion.Satisfied<>(attempt);
            }
        }
    }

    private AttemptRecord<OBSERVED, RESULT> evaluate(AttemptRecord.Phase phase, long number,
            long attemptStarted, long retrievalStarted, AttemptRecord.Progress progress) {
        AttemptRecord<OBSERVED, RESULT> interrupted = interruptedBefore(phase, number, attemptStarted, progress);
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
            return new AttemptRecord<>(number, phase, timing, evaluator.condition, progress,
                    new AttemptRecord.Outcome.SourceRetrievalFailed<>(failure));
        }

        long observed = now(configuration);
        if (currentThread().isInterrupted()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            long completed = now(configuration);
            var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
            return new AttemptRecord<>(number, phase, timing, evaluator.condition, progress,
                    new AttemptRecord.Outcome.SourceInterrupted<>(actual, interruption));
        }

        var execution = evaluator.probe(actual);
        long completed = now(configuration);

        var evaluation = ConditionAwaitAttempt.withInterruption(execution);

        AttemptRecord.Timing timing = afterObservation(attemptStarted,
                retrievalStarted, observed, completed);
        var condition = evaluation.diagnosticCondition(evaluator.condition);
        AttemptRecord.Outcome<OBSERVED, RESULT> outcome = switch (evaluation) {
            case AttemptResult.Evaluated<? extends RESULT> value -> new Evaluated<>(
                    actual, value.result());
            case AttemptResult.Uncontrolled<?> failure -> new AttemptRecord.Outcome.ConditionFailed<>(
                    actual, failure.cause());
        };
        return new AttemptRecord<>(number, phase, timing, condition,
                evaluation.progress() == null ? progress : evaluation.progress(), outcome);
    }

    private AttemptRecord.Timing afterObservation(long attemptStarted, long retrievalStarted,
            long observed, long completed) {
        return new AttemptRecord.Timing(offset(attemptStarted),
                offset(retrievalStarted),
                offset(observed),
                offset(completed));
    }

    private AttemptRecord<OBSERVED, RESULT> waitBeforeAttempt(long delay,
            AttemptRecord.Phase phase, long number,
            long attemptStarted, AttemptRecord.Progress progress) {
        long deadline = attemptStarted + delay;
        long remaining;
        while ((remaining = remaining(now(configuration), deadline)) > 0) {
            AttemptRecord<OBSERVED, RESULT> interrupted = interruptedBefore(phase, number, attemptStarted, progress);
            if (interrupted != null) {
                return interrupted;
            }
            try {
                configuration.parker().accept(remaining);
            } catch (Throwable failure) {
                return waitingFailure(phase, number, attemptStarted, progress, failure);
            }
            interrupted = interruptedBefore(phase, number, attemptStarted, progress);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return interruptedBefore(phase, number, attemptStarted, progress);
    }

    private AttemptRecord<OBSERVED, RESULT> interruptedBefore(AttemptRecord.Phase phase, long number,
            long attemptStarted, AttemptRecord.Progress progress) {
        if (!currentThread().isInterrupted()) {
            return null;
        }
        return waitingFailure(phase, number, attemptStarted, progress,
                new InterruptedException("caller thread interrupt flag was set"));
    }

    private AttemptRecord<OBSERVED, RESULT> waitingFailure(AttemptRecord.Phase phase, long number,
            long attemptStarted, AttemptRecord.Progress progress, Throwable failure) {
        long completed = now(configuration);
        var timing = new AttemptRecord.Timing(offset(attemptStarted), null, null, offset(completed));
        return new AttemptRecord<>(number, phase, timing, evaluator.condition, progress,
                new AttemptRecord.Outcome.WaitingFailed<>(failure));
    }

    private long completedNanos(AttemptRecord<?, ?> attempt) {
        return started + attempt.timing().completionOffset().toNanos();
    }

    private Duration offset(long stage) {
        return Duration.ofNanos(stage - started);
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
