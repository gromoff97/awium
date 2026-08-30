package io.github.gromoff97.awium.internal.engine;

import io.github.gromoff97.awium.internal.condition.ConditionAssessment;
import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.results.AwaitAttempt.Phase.ACQUISITION;
import static io.github.gromoff97.awium.results.AwaitAttempt.Phase.PERSISTENCE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
public record WaitEngine(WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {

    public <Observed, Result> WaitCompletion<Observed, Result> waitFor(Source<? extends Observed> source,
            Function<? super Observed, ? extends ConditionAssessment<? extends Result>> evaluator) {
        return waitFor(source, evaluator, ignored -> {});
    }

    public <Observed, Result> RecordedWait<Observed, Result> recordedWaitFor(Source<? extends Observed> source,
            Function<? super Observed, ? extends ConditionAssessment<? extends Result>> evaluator) {
        var attempts = new ArrayList<AwaitAttempt<Observed, Result>>();
        WaitCompletion<Observed, Result> outcome = waitFor(source, evaluator, attempt -> {
            if (attempts.isEmpty() || !equivalent(attempts.getLast(), attempt)) {
                attempts.add(attempt);
            } else {
                attempts.set(attempts.size() - 1, attempt);
            }
        });
        return new RecordedWait<>(outcome, attempts);
    }

    private <Observed, Result> WaitCompletion<Observed, Result> waitFor(Source<? extends Observed> source,
            Function<? super Observed, ? extends ConditionAssessment<? extends Result>> evaluator,
            Consumer<AwaitAttempt<Observed, Result>> recorder) {
        configuration.validatePair();
        long started = clock.getAsLong();
        ObservationEvaluator<Observed, Result> observations = new ObservationEvaluator<>(source, evaluator, clock, started);
        WaitCompletion<Observed, Result> acquisition = acquire(observations, recorder, started);
        if (!(acquisition instanceof WaitCompletion.Satisfied<Observed, Result> acquired)
                || configuration.persistenceNanos() == 0) {
            return acquisition;
        }
        return persist(observations, recorder, started, acquired);
    }

    private <Observed, Result> WaitCompletion<Observed, Result> acquire(ObservationEvaluator<Observed, Result> observations,
            Consumer<AwaitAttempt<Observed, Result>> recorder,
            long started) {
        long deadline = after(started, configuration.upToNanos());
        AwaitAttempt<Observed, Result> lastUnsatisfied = null;
        long completed = started;

        for (long number = 1;; number++) {
            long attemptStarted = completed;
            AwaitAttempt<Observed, Result> preparationFailure = number == 1
                    ? interruptedBefore(ACQUISITION, number, started, attemptStarted)
                    : waitBeforeAttempt(min(configuration.everyNanos(), remaining(completed, deadline)),
                            ACQUISITION, number, started, attemptStarted);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled<>(preparationFailure);
            }

            long retrievalStarted = clock.getAsLong();
            if (number > 1 && reached(retrievalStarted, deadline)) {
                return new WaitCompletion.TimeoutBetweenObservations<>(retrievalStarted - started, lastUnsatisfied);
            }

            AwaitAttempt<Observed, Result> attempt = observations.evaluate(ACQUISITION,
                    number, attemptStarted, retrievalStarted);
            completed = completedNanos(started, attempt);
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

    private <Observed, Result> WaitCompletion<Observed, Result> persist(ObservationEvaluator<Observed, Result> observations,
            Consumer<AwaitAttempt<Observed, Result>> recorder,
            long started,
            WaitCompletion.Satisfied<Observed, Result> acquired) {
        long acquiredAt = completedNanos(started, acquired.attempt());
        long deadline = after(acquiredAt, configuration.persistenceNanos());
        long completed = acquiredAt;

        for (long number = acquired.attempt().number() + 1;; number++) {
            long attemptStarted = completed;
            long delay = min(configuration.everyNanos(), remaining(completed, deadline));
            AwaitAttempt<Observed, Result> preparationFailure = waitBeforeAttempt(delay,
                    PERSISTENCE, number, started, attemptStarted);
            if (preparationFailure != null) {
                recorder.accept(preparationFailure);
                return new WaitCompletion.Uncontrolled<>(preparationFailure);
            }

            long retrievalStarted = clock.getAsLong();
            AwaitAttempt<Observed, Result> attempt = observations.evaluate(PERSISTENCE,
                    number, attemptStarted, retrievalStarted);
            completed = completedNanos(started, attempt);
            recorder.accept(attempt);
            if (failed(attempt)) {
                return new WaitCompletion.Uncontrolled<>(attempt);
            }
            if (unsatisfied(attempt)) {
                return new WaitCompletion.PersistenceFailure<>(acquiredAt - started, attempt);
            }
            if (reached(completed, deadline)) {
                return new WaitCompletion.Satisfied<>(attempt);
            }
        }
    }

    private <Observed, Result> AwaitAttempt<Observed, Result> waitBeforeAttempt(long delay,
            AwaitAttempt.Phase phase, long number, long started,
            long attemptStarted) {
        AwaitAttempt<Observed, Result> failure = parkUntil(after(attemptStarted, delay),
                phase, number, started, attemptStarted);
        return failure != null
                ? failure
                : interruptedBefore(phase, number, started, attemptStarted);
    }

    private <Observed, Result> AwaitAttempt<Observed, Result> parkUntil(long deadline,
            AwaitAttempt.Phase phase, long number, long started,
            long attemptStarted) {
        long remaining;
        while ((remaining = remaining(clock.getAsLong(), deadline)) > 0) {
            try {
                parker.accept(remaining);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                return waitingFailure(phase, number, started, attemptStarted, failure);
            }
            AwaitAttempt<Observed, Result> interrupted = interruptedBefore(phase, number, started, attemptStarted);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return null;
    }

    private <Observed, Result> AwaitAttempt<Observed, Result> interruptedBefore(AwaitAttempt.Phase phase, long number, long started,
            long attemptStarted) {
        if (!currentThread().isInterrupted()) {
            return null;
        }
        return waitingFailure(phase, number, started, attemptStarted,
                new InterruptedException("caller thread interrupt flag was set"));
    }

    private <Observed, Result> AwaitAttempt<Observed, Result> waitingFailure(AwaitAttempt.Phase phase, long number, long started,
            long attemptStarted, Throwable failure) {
        if (failure instanceof InterruptedException) {
            currentThread().interrupt();
        }
        long completed = clock.getAsLong();
        var timing = new AwaitAttempt.Timing.BeforeRetrieval(offset(started, attemptStarted), offset(started, completed));
        return new AwaitAttempt<>(number, phase, new AwaitAttempt.Outcome.WaitingFailed<>(timing, failure));
    }

    private static boolean satisfied(AwaitAttempt<?, ?> attempt) {
        return attempt.outcome() instanceof AwaitAttempt.Outcome.Satisfied<?, ?>;
    }

    private static boolean unsatisfied(AwaitAttempt<?, ?> attempt) {
        return attempt.outcome() instanceof AwaitAttempt.Outcome.Unsatisfied<?, ?>;
    }

    private static boolean failed(AwaitAttempt<?, ?> attempt) {
        return !satisfied(attempt) && !unsatisfied(attempt);
    }

    private static long completedNanos(long started, AwaitAttempt<?, ?> attempt) {
        return started + attempt.outcome().timing().completionOffset().toNanos();
    }

    private static Duration offset(long started, long stage) {
        return Duration.ofNanos(stage - started);
    }

    private static long after(long now, long durationNanos) {
        return now + durationNanos;
    }

    private static boolean reached(long now, long deadline) {
        return now - deadline >= 0;
    }

    private static long remaining(long now, long deadline) {
        return max(deadline - now, 0);
    }

    public record RecordedWait<Observed, Result>(WaitCompletion<Observed, Result> outcome,
            List<AwaitAttempt<Observed, Result>> attempts) {

        public RecordedWait {
            attempts = List.copyOf(attempts);
        }
    }

    private static boolean equivalent(AwaitAttempt<?, ?> left,
            AwaitAttempt<?, ?> right) {
        if (left.phase() != right.phase()) {
            return false;
        }
        return switch (left.outcome()) {
            case AwaitAttempt.Outcome.Satisfied<?, ?> value
                    when right.outcome() instanceof AwaitAttempt.Outcome.Satisfied<?, ?> other ->
                    value.observed() == other.observed() && value.result() == other.result();
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> value
                    when right.outcome() instanceof AwaitAttempt.Outcome.Unsatisfied<?, ?> other ->
                    value.observed() == other.observed()
                            && value.mismatch().equals(other.mismatch())
                            && value.assertion() == other.assertion()
                            && value.context().equals(other.context());
            default -> false;
        };
    }
}
