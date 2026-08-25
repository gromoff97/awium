package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.await.AwaitAttempt;
import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.sources.Source;

import java.time.Duration;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.await.AwaitAttempt.Phase.ACQUISITION;
import static io.github.gromoff97.awium.await.AwaitAttempt.Phase.STABILIZATION;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
public record WaitEngine(WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {

    public <S, R> WaitOutcome<S, R> waitFor(Source<? extends S> source,
            CheckedFunction<? super S, ? extends Evaluation<? extends R>> evaluator) {
        configuration.validatePair();
        long started = clock.getAsLong();
        var observations = new ObservationEvaluator(clock);
        WaitOutcome<S, R> acquisition = acquire(source, evaluator, observations, started);
        if (!(acquisition instanceof WaitOutcome.Satisfied<S, R> acquired)
                || configuration.stableForNanos() == 0) {
            return acquisition;
        }
        return stabilize(source, evaluator, observations, started, acquired);
    }

    private <S, R> WaitOutcome<S, R> acquire(Source<? extends S> source,
            CheckedFunction<? super S, ? extends Evaluation<? extends R>> evaluator,
            ObservationEvaluator observations, long started) {
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
                    return new WaitOutcome.Uncontrolled<>(parked);
                }
            }

            AwaitAttempt<S, R> interrupted = interruptedBefore(
                    ACQUISITION, number, started, attemptStarted);
            if (interrupted != null) {
                return new WaitOutcome.Uncontrolled<>(interrupted);
            }

            long retrievalStarted = clock.getAsLong();
            if (number > 1 && reached(retrievalStarted, deadline)) {
                return new WaitOutcome.TimeoutBetweenObservations<>(
                        started, retrievalStarted, lastUnsatisfied);
            }

            ObservationEvaluator.EvaluatedAttempt<S, R> evaluated = observations.evaluate(
                    source, evaluator, ACQUISITION,
                    number, started, attemptStarted, retrievalStarted);
            AwaitAttempt<S, R> attempt = evaluated.attempt();
            completed = evaluated.completedNanos();
            if (failed(attempt)) {
                return new WaitOutcome.Uncontrolled<>(attempt);
            }
            if (satisfied(attempt)) {
                return reached(completed, deadline)
                        ? new WaitOutcome.LateSatisfiedTimeout<>(started, attempt)
                        : new WaitOutcome.Satisfied<>(attempt);
            }
            if (reached(completed, deadline)) {
                return new WaitOutcome.LateUnsatisfiedTimeout<>(started, attempt);
            }
            lastUnsatisfied = attempt;
        }
    }

    private <S, R> WaitOutcome<S, R> stabilize(Source<? extends S> source,
            CheckedFunction<? super S, ? extends Evaluation<? extends R>> evaluator,
            ObservationEvaluator observations, long started,
            WaitOutcome.Satisfied<S, R> acquired) {
        long acquiredAt = completedNanos(started, acquired.attempt());
        long deadline = after(acquiredAt, configuration.stableForNanos());
        long completed = acquiredAt;

        for (long number = acquired.attempt().number() + 1;; number++) {
            long attemptStarted = completed;
            long delay = min(configuration.everyNanos(), remaining(completed, deadline));
            AwaitAttempt<S, R> parked = parkUntil(after(completed, delay),
                    STABILIZATION, number, started, attemptStarted);
            if (parked != null) {
                return new WaitOutcome.Uncontrolled<>(parked);
            }

            AwaitAttempt<S, R> interrupted = interruptedBefore(
                    STABILIZATION, number, started, attemptStarted);
            if (interrupted != null) {
                return new WaitOutcome.Uncontrolled<>(interrupted);
            }

            long retrievalStarted = clock.getAsLong();
            ObservationEvaluator.EvaluatedAttempt<S, R> evaluated = observations.evaluate(
                    source, evaluator, STABILIZATION,
                    number, started, attemptStarted, retrievalStarted);
            AwaitAttempt<S, R> attempt = evaluated.attempt();
            completed = evaluated.completedNanos();
            if (failed(attempt)) {
                return new WaitOutcome.Uncontrolled<>(attempt);
            }
            if (unsatisfied(attempt)) {
                return new WaitOutcome.StabilityLoss<>(started, acquiredAt, attempt);
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
                if (failure instanceof InterruptedException) {
                    restoreInterrupt();
                }
                return waitingFailure(phase, number, started, attemptStarted, failure);
            }
            AwaitAttempt<S, R> interrupted = interruptedBefore(
                    phase, number, started, attemptStarted);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return null;
    }

    private <S, R> AwaitAttempt<S, R> interruptedBefore(
            AwaitAttempt.Phase phase, long number, long started,
            long attemptStarted) {
        if (!currentThread().isInterrupted()) {
            return null;
        }
        return waitingFailure(phase, number, started, attemptStarted,
                new InterruptedException("caller thread interrupt flag was set"));
    }

    private <S, R> AwaitAttempt<S, R> waitingFailure(
            AwaitAttempt.Phase phase, long number, long started,
            long attemptStarted, Throwable failure) {
        if (failure instanceof InterruptedException) {
            restoreInterrupt();
        }
        long completed = clock.getAsLong();
        return new AwaitAttempt<>(number, phase,
                new AwaitAttempt.Outcome.WaitingFailed<>(
                        new AwaitAttempt.Timing.BeforeRetrieval(
                                offset(started, attemptStarted),
                                offset(started, completed)), failure));
    }

    private static boolean satisfied(AwaitAttempt<?, ?> attempt) {
        return attempt.outcome() instanceof AwaitAttempt.Outcome.Satisfied<?, ?>;
    }

    private static boolean unsatisfied(AwaitAttempt<?, ?> attempt) {
        return attempt.outcome() instanceof AwaitAttempt.Outcome.Unsatisfied<?, ?>
                || attempt.outcome() instanceof AwaitAttempt.Outcome.AssertionUnsatisfied<?, ?>;
    }

    private static boolean failed(AwaitAttempt<?, ?> attempt) {
        return !satisfied(attempt) && !unsatisfied(attempt);
    }

    private static long completedNanos(long started, AwaitAttempt<?, ?> attempt) {
        Duration completion = switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Satisfied<?, ?> value -> value.timing().completionOffset();
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> value -> value.timing().completionOffset();
            case AwaitAttempt.Outcome.AssertionUnsatisfied<?, ?> value -> value.timing().completionOffset();
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> value -> value.timing().completionOffset();
            case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> value -> value.timing().completionOffset();
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> value -> value.timing().completionOffset();
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> value -> value.timing().completionOffset();
        };
        return started + completion.toNanos();
    }

    private static Duration offset(long started, long stage) {
        return Duration.ofNanos(stage - started);
    }

    private static void restoreInterrupt() {
        currentThread().interrupt();
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
}
