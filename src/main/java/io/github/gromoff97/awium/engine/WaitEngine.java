package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.await.AwaitAttempt;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.await.AwaitAttempt.Phase.ACQUISITION;
import static io.github.gromoff97.awium.await.AwaitAttempt.Phase.PERSISTENCE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
public record WaitEngine(WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {

    public <S, R> WaitOutcome<S, R> waitFor(Source<? extends S> source,
            Function<? super S, ? extends Evaluation<? extends R>> evaluator) {
        return waitFor(source, evaluator, ignored -> {});
    }

    public <S, R> Execution<S, R> recordedWaitFor(Source<? extends S> source,
            Function<? super S, ? extends Evaluation<? extends R>> evaluator) {
        var attempts = new ArrayList<AwaitAttempt<S, R>>();
        WaitOutcome<S, R> outcome = waitFor(source, evaluator, attempt -> {
            if (attempts.isEmpty() || !equivalent(attempts.getLast(), attempt)) {
                attempts.add(attempt);
            }
        });
        return new Execution<>(outcome, attempts);
    }

    private <S, R> WaitOutcome<S, R> waitFor(Source<? extends S> source,
            Function<? super S, ? extends Evaluation<? extends R>> evaluator,
            Consumer<AwaitAttempt<S, R>> recorder) {
        configuration.validatePair();
        long started = clock.getAsLong();
        ObservationEvaluator<S, R> observations = new ObservationEvaluator<>(source, evaluator, clock, started);
        WaitOutcome<S, R> acquisition = acquire(observations, recorder, started);
        if (!(acquisition instanceof WaitOutcome.Satisfied<S, R> acquired)
                || configuration.persistenceNanos() == 0) {
            return acquisition;
        }
        return persist(observations, recorder, started, acquired);
    }

    private <S, R> WaitOutcome<S, R> acquire(ObservationEvaluator<S, R> observations,
            Consumer<AwaitAttempt<S, R>> recorder,
            long started) {
        long deadline = after(started, configuration.upToNanos());
        AwaitAttempt<S, R> lastUnsatisfied = null;
        long completed = started;

        for (long number = 1;; number++) {
            long attemptStarted = completed;
            if (number > 1) {
                long delay = min(configuration.everyNanos(), remaining(completed, deadline));
                AwaitAttempt<S, R> parked = parkUntil(after(completed, delay),
                        ACQUISITION, number, started, attemptStarted);
                if (parked != null) {
                    recorder.accept(parked);
                    return new WaitOutcome.Uncontrolled<>(parked);
                }
            }

            AwaitAttempt<S, R> interrupted = interruptedBefore(ACQUISITION, number, started, attemptStarted);
            if (interrupted != null) {
                recorder.accept(interrupted);
                return new WaitOutcome.Uncontrolled<>(interrupted);
            }

            long retrievalStarted = clock.getAsLong();
            if (number > 1 && reached(retrievalStarted, deadline)) {
                return new WaitOutcome.TimeoutBetweenObservations<>(retrievalStarted - started, lastUnsatisfied);
            }

            AwaitAttempt<S, R> attempt = observations.evaluate(ACQUISITION,
                    number, attemptStarted, retrievalStarted);
            completed = completedNanos(started, attempt);
            recorder.accept(attempt);
            if (failed(attempt)) {
                return new WaitOutcome.Uncontrolled<>(attempt);
            }
            if (reached(completed, deadline)) {
                return new WaitOutcome.LateTimeout<>(attempt);
            }
            if (satisfied(attempt)) {
                return new WaitOutcome.Satisfied<>(attempt);
            }
            lastUnsatisfied = attempt;
        }
    }

    private <S, R> WaitOutcome<S, R> persist(ObservationEvaluator<S, R> observations,
            Consumer<AwaitAttempt<S, R>> recorder,
            long started,
            WaitOutcome.Satisfied<S, R> acquired) {
        long acquiredAt = completedNanos(started, acquired.attempt());
        long deadline = after(acquiredAt, configuration.persistenceNanos());
        long completed = acquiredAt;

        for (long number = acquired.attempt().number() + 1;; number++) {
            long attemptStarted = completed;
            long delay = min(configuration.everyNanos(), remaining(completed, deadline));
            AwaitAttempt<S, R> parked = parkUntil(after(completed, delay),
                    PERSISTENCE, number, started, attemptStarted);
            if (parked != null) {
                recorder.accept(parked);
                return new WaitOutcome.Uncontrolled<>(parked);
            }

            AwaitAttempt<S, R> interrupted = interruptedBefore(PERSISTENCE, number, started, attemptStarted);
            if (interrupted != null) {
                recorder.accept(interrupted);
                return new WaitOutcome.Uncontrolled<>(interrupted);
            }

            long retrievalStarted = clock.getAsLong();
            AwaitAttempt<S, R> attempt = observations.evaluate(PERSISTENCE,
                    number, attemptStarted, retrievalStarted);
            completed = completedNanos(started, attempt);
            recorder.accept(attempt);
            if (failed(attempt)) {
                return new WaitOutcome.Uncontrolled<>(attempt);
            }
            if (unsatisfied(attempt)) {
                return new WaitOutcome.PersistenceFailure<>(acquiredAt - started, attempt);
            }
            if (reached(completed, deadline)) {
                return new WaitOutcome.Satisfied<>(attempt);
            }
        }
    }

    private <S, R> AwaitAttempt<S, R> parkUntil(long deadline,
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
            AwaitAttempt<S, R> interrupted = interruptedBefore(phase, number, started, attemptStarted);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return null;
    }

    private <S, R> AwaitAttempt<S, R> interruptedBefore(AwaitAttempt.Phase phase, long number, long started,
            long attemptStarted) {
        if (!currentThread().isInterrupted()) {
            return null;
        }
        return waitingFailure(phase, number, started, attemptStarted,
                new InterruptedException("caller thread interrupt flag was set"));
    }

    private <S, R> AwaitAttempt<S, R> waitingFailure(AwaitAttempt.Phase phase, long number, long started,
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

    public record Execution<S, R>(WaitOutcome<S, R> outcome,
            List<AwaitAttempt<S, R>> attempts) {

        public Execution {
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
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> value
                    when right.outcome() instanceof AwaitAttempt.Outcome.WaitingFailed<?, ?> other ->
                    value.failure() == other.failure();
            case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> value
                    when right.outcome() instanceof AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> other ->
                    value.failure() == other.failure();
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> value
                    when right.outcome() instanceof AwaitAttempt.Outcome.SourceInterrupted<?, ?> other ->
                    value.observed() == other.observed() && value.failure() == other.failure();
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> value
                    when right.outcome() instanceof AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> other ->
                    value.observed() == other.observed()
                            && value.failure() == other.failure()
                            && value.context().equals(other.context());
            default -> false;
        };
    }
}
