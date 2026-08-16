package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.sources.Source;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Origin;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Origin.WAITING;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Satisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled.BeforeObservation;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Unsatisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.LateSatisfiedTimeout;
import static io.github.gromoff97.awium.engine.WaitOutcome.LateUnsatisfiedTimeout;
import static io.github.gromoff97.awium.engine.WaitOutcome.StabilityLoss;
import static io.github.gromoff97.awium.engine.WaitOutcome.TimeoutBetweenObservations;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
public record WaitEngine(WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {

    public <S, R> WaitOutcome<R> waitFor(Source<? extends S> source,
            CheckedFunction<? super S, ? extends Evaluation<? extends R>> evaluator) {
        configuration.validatePair();
        long started = clock.getAsLong();
        var observations = new ObservationEvaluator(clock);
        WaitOutcome<R> acquisition = acquire(source, evaluator, observations, started);
        if (!(acquisition instanceof Satisfied<R> acquired)
                || configuration.stableForNanos() == 0) {
            return acquisition;
        }
        return stabilize(source, evaluator, observations, started, acquired);
    }

    private <S, R> WaitOutcome<R> acquire(Source<? extends S> source,
            CheckedFunction<? super S, ? extends Evaluation<? extends R>> evaluator,
            ObservationEvaluator observations, long started) {
        long deadline = after(started, configuration.upToNanos());
        Unsatisfied<R> lastUnsatisfied = null;
        long completed = started;

        for (long number = 1;; number++) {
            if (number > 1) {
                long delay = min(configuration.everyNanos(), remaining(completed, deadline));
                Uncontrolled<R> parked = parkUntil(after(completed, delay), number);
                if (parked != null) {
                    return parked;
                }
            }

            Uncontrolled<R> interrupted = interruptedBefore(WAITING, number);
            if (interrupted != null) {
                return interrupted;
            }

            long before = clock.getAsLong();
            if (number > 1 && reached(before, deadline)) {
                return new TimeoutBetweenObservations<>(started, before, lastUnsatisfied);
            }

            Attempt<R> observed = observations.evaluate(source, evaluator, number);
            completed = observed.completedNanos();
            if (observed instanceof Uncontrolled<R> failure) {
                return failure;
            }

            if (observed instanceof Satisfied<R> satisfied) {
                if (reached(completed, deadline)) {
                    return new LateSatisfiedTimeout<>(started, satisfied);
                }
                return satisfied;
            }
            Unsatisfied<R> unsatisfied = (Unsatisfied<R>) observed;
            if (reached(completed, deadline)) {
                return new LateUnsatisfiedTimeout<>(started, unsatisfied);
            }
            lastUnsatisfied = unsatisfied;
        }
    }

    private <S, R> WaitOutcome<R> stabilize(Source<? extends S> source,
            CheckedFunction<? super S, ? extends Evaluation<? extends R>> evaluator,
            ObservationEvaluator observations, long started, Satisfied<R> acquired) {
        long acquiredAt = acquired.completedNanos();
        long deadline = after(acquiredAt, configuration.stableForNanos());
        long completed = acquiredAt;

        for (long number = acquired.number() + 1;; number++) {
            long delay = min(configuration.everyNanos(), remaining(completed, deadline));
            Uncontrolled<R> parked = parkUntil(after(completed, delay), number);
            if (parked != null) {
                return parked;
            }

            Uncontrolled<R> interrupted = interruptedBefore(WAITING, number);
            if (interrupted != null) {
                return interrupted;
            }

            Attempt<R> observed = observations.evaluate(source, evaluator, number);
            completed = observed.completedNanos();
            if (observed instanceof Uncontrolled<R> failure) {
                return failure;
            }
            if (observed instanceof Unsatisfied<R> unsatisfied) {
                return new StabilityLoss<>(started, acquiredAt, unsatisfied);
            }
            Satisfied<R> satisfied = (Satisfied<R>) observed;
            if (reached(completed, deadline)) {
                return satisfied;
            }
        }
    }

    private <R> Uncontrolled<R> parkUntil(long deadline, long attemptNumber) {
        long remaining;
        while ((remaining = remaining(clock.getAsLong(), deadline)) > 0) {
            try {
                parker.accept(remaining);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable uncontrolled) {
                if (uncontrolled instanceof InterruptedException interruption) {
                    return interruptedBefore(WAITING, interruption, attemptNumber);
                }
                return new BeforeObservation<>(WAITING, uncontrolled, attemptNumber, clock.getAsLong());
            }
            Uncontrolled<R> interrupted = interruptedBefore(WAITING, attemptNumber);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return null;
    }

    private <R> Uncontrolled<R> interruptedBefore(Origin origin, long number) {
        return interrupted()
                ? interruptedBefore(origin, new InterruptedException("caller thread interrupt flag was set"), number)
                : null;
    }

    private static boolean interrupted() {
        return currentThread().isInterrupted();
    }

    private <R> Uncontrolled<R> interruptedBefore(Origin origin, InterruptedException interrupted, long number) {
        restoreInterrupt();
        return new BeforeObservation<>(origin, interrupted, number, clock.getAsLong());
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
