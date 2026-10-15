package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.AwaitAttempt.Phase.ACQUISITION;
import static io.github.gromoff97.awium.AwaitAttempt.Phase.PERSISTENCE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

/** One wait execution, including its observations, timing, and condition session. */
@SuppressWarnings("removal")
record WaitEngine<OBSERVED, RESULT>(WaitConfiguration configuration, LongSupplier clock,
        LongConsumer parker, Source<? extends OBSERVED> source,
        ConditionSession<? super OBSERVED, ? extends RESULT> evaluator, long started) {

    WaitEngine(WaitConfiguration configuration, LongSupplier clock, LongConsumer parker,
            Source<? extends OBSERVED> source, ConditionSession<? super OBSERVED, ? extends RESULT> evaluator) {
        this(configuration, clock, parker, source, evaluator, now(clock));
    }

    WaitCompletion<OBSERVED, RESULT> waitFor() {
        return waitFor(ignored -> {});
    }

    WaitCompletion<OBSERVED, RESULT> waitFor(Consumer<AwaitAttempt<OBSERVED, RESULT>> recorder) {
        WaitCompletion<OBSERVED, RESULT> acquisition = acquire(recorder);
        if (!(acquisition instanceof WaitCompletion.Satisfied<OBSERVED, RESULT> acquired)
                || configuration.persistenceNanos() == 0) {
            return acquisition;
        }
        return persist(recorder, acquired);
    }

    private WaitCompletion<OBSERVED, RESULT> acquire(Consumer<AwaitAttempt<OBSERVED, RESULT>> recorder) {
        long deadline = started + configuration.upToNanos();
        AwaitAttempt<OBSERVED, RESULT> lastUnsatisfied = null;
        long completed = started;

        for (long number = 1;; number++) {
            long attemptStarted = completed;
            AwaitAttempt<OBSERVED, RESULT> preparationFailure = number == 1
                    ? interruptedBefore(ACQUISITION, number, attemptStarted)
                    : waitBeforeAttempt(min(configuration.everyNanos(), remaining(completed, deadline)),
                            ACQUISITION, number, attemptStarted);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled<>(preparationFailure);
            }

            long retrievalStarted = now(clock);
            if (number > 1 && reached(retrievalStarted, deadline) && !currentThread().isInterrupted()) {
                return new WaitCompletion.TimeoutBetweenObservations<>(retrievalStarted - started, lastUnsatisfied);
            }

            AwaitAttempt<OBSERVED, RESULT> attempt = evaluate(ACQUISITION,
                    number, attemptStarted, retrievalStarted);
            completed = completedNanos(attempt);
            recorder.accept(attempt);
            if (failed(attempt)) {
                return new WaitCompletion.Uncontrolled<>(attempt);
            }
            if (reached(completed, deadline)) {
                return new WaitCompletion.LateTimeout<>(attempt);
            }
            if (satisfied(attempt)) {
                return new WaitCompletion.Satisfied<>(attempt);
            }
            lastUnsatisfied = attempt;
        }
    }

    private WaitCompletion<OBSERVED, RESULT> persist(Consumer<AwaitAttempt<OBSERVED, RESULT>> recorder,
            WaitCompletion.Satisfied<OBSERVED, RESULT> acquired) {
        long acquiredAt = completedNanos(acquired.attempt());
        long deadline = acquiredAt + configuration.persistenceNanos();
        long completed = acquiredAt;

        for (long number = acquired.attempt().number() + 1;; number++) {
            long attemptStarted = completed;
            long delay = min(configuration.everyNanos(), remaining(completed, deadline));
            AwaitAttempt<OBSERVED, RESULT> preparationFailure = waitBeforeAttempt(delay,
                    PERSISTENCE, number, attemptStarted);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled<>(preparationFailure);
            }

            long retrievalStarted = now(clock);
            AwaitAttempt<OBSERVED, RESULT> attempt = evaluate(PERSISTENCE,
                    number, attemptStarted, retrievalStarted);
            completed = completedNanos(attempt);
            recorder.accept(attempt);
            if (failed(attempt)) {
                return new WaitCompletion.Uncontrolled<>(attempt);
            }
            if (!satisfied(attempt)) {
                return new WaitCompletion.PersistenceFailure<>(acquiredAt - started, attempt);
            }
            if (reached(completed, deadline)) {
                return new WaitCompletion.Satisfied<>(attempt);
            }
        }
    }

    private AwaitAttempt<OBSERVED, RESULT> evaluate(AwaitAttempt.Phase phase, long number,
            long attemptStarted, long retrievalStarted) {
        AwaitAttempt<OBSERVED, RESULT> interrupted = interruptedBefore(phase, number, attemptStarted);
        if (interrupted != null) {
            return interrupted;
        }
        OBSERVED actual;
        try {
            actual = source.get();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            if (failure instanceof InterruptedException) {
                currentThread().interrupt();
            }
            long completed = now(clock);
            var timing = new AwaitAttempt.Timing.BeforeObservation(offset(attemptStarted),
                    offset(retrievalStarted), offset(completed));
            return new AwaitAttempt<>(number, phase,
                    new AwaitAttempt.Outcome.SourceRetrievalFailed<>(timing, failure));
        }

        long observed = now(clock);
        if (currentThread().isInterrupted()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            long completed = now(clock);
            var timing = afterObservation(attemptStarted, retrievalStarted, observed, completed);
            return new AwaitAttempt<>(number, phase,
                    new AwaitAttempt.Outcome.SourceInterrupted<>(timing, actual, interruption));
        }

        ConditionResult<? extends RESULT> evaluation = evaluator.apply(actual);
        long completed = now(clock);

        evaluation = ConditionSession.withInterruption(evaluation);

        AwaitAttempt.Timing.AfterObservation timing = afterObservation(attemptStarted,
                retrievalStarted, observed, completed);
        return new AwaitAttempt<>(number, phase,
                new AwaitAttempt.Outcome.Evaluated<>(timing, actual, evaluation));
    }

    private AwaitAttempt.Timing.AfterObservation afterObservation(long attemptStarted, long retrievalStarted,
            long observed, long completed) {
        return new AwaitAttempt.Timing.AfterObservation(offset(attemptStarted),
                offset(retrievalStarted),
                offset(observed),
                offset(completed));
    }

    private AwaitAttempt<OBSERVED, RESULT> waitBeforeAttempt(long delay,
            AwaitAttempt.Phase phase, long number,
            long attemptStarted) {
        long deadline = attemptStarted + delay;
        long remaining;
        while ((remaining = remaining(now(clock), deadline)) > 0) {
            AwaitAttempt<OBSERVED, RESULT> interrupted = interruptedBefore(phase, number, attemptStarted);
            if (interrupted != null) {
                return interrupted;
            }
            try {
                parker.accept(remaining);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                return waitingFailure(phase, number, attemptStarted, failure);
            }
            interrupted = interruptedBefore(phase, number, attemptStarted);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return interruptedBefore(phase, number, attemptStarted);
    }

    private AwaitAttempt<OBSERVED, RESULT> interruptedBefore(AwaitAttempt.Phase phase, long number,
            long attemptStarted) {
        if (!currentThread().isInterrupted()) {
            return null;
        }
        return waitingFailure(phase, number, attemptStarted,
                new InterruptedException("caller thread interrupt flag was set"));
    }

    private AwaitAttempt<OBSERVED, RESULT> waitingFailure(AwaitAttempt.Phase phase, long number,
            long attemptStarted, Throwable failure) {
        if (failure instanceof InterruptedException) {
            currentThread().interrupt();
        }
        long completed = now(clock);
        var timing = new AwaitAttempt.Timing.BeforeRetrieval(offset(attemptStarted), offset(completed));
        return new AwaitAttempt<>(number, phase, new AwaitAttempt.Outcome.WaitingFailed<>(timing, failure));
    }

    private static boolean satisfied(AwaitAttempt<?, ?> attempt) {
        return attempt.outcome() instanceof AwaitAttempt.Outcome.Evaluated<?, ?> value
                && value.evaluation() instanceof ConditionResult.Satisfied<?>;
    }

    private static boolean failed(AwaitAttempt<?, ?> attempt) {
        return !(attempt.outcome() instanceof AwaitAttempt.Outcome.Evaluated<?, ?> value)
                || value.evaluation() instanceof ConditionResult.Uncontrolled<?>;
    }

    private long completedNanos(AwaitAttempt<?, ?> attempt) {
        return started + attempt.outcome().timing().completionOffset().toNanos();
    }

    private Duration offset(long stage) {
        return Duration.ofNanos(stage - started);
    }

    private static long now(LongSupplier clock) {
        boolean interrupted = currentThread().isInterrupted();
        try {
            return clock.getAsLong();
        } catch (Throwable failure) {
            interrupted |= failure instanceof InterruptedException;
            throw failure;
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
