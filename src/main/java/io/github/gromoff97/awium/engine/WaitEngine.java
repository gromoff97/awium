package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.sources.Source;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("removal")
public final class WaitEngine {

    private final WaitConfiguration config;
    private final LongSupplier clock;
    private final LongConsumer parker;

    public WaitEngine(WaitConfiguration config, LongSupplier clock,
            LongConsumer parker) {
        this.config = requireNonNull(config);
        this.clock = requireNonNull(clock);
        this.parker = requireNonNull(parker);
    }

    public <S, R> WaitOutcome<R> waitFor(
            Source<S> source, RuntimeCondition<S, R> condition) {
        requireNonNull(source);
        requireNonNull(condition);
        long started = clock.getAsLong();
        long acquisitionDeadline = after(started, config.upToNanos());
        Attempt.Unsatisfied<R> lastUnsatisfied = null;
        long number = 1;

        for (;;) {
            Attempt.Uncontrolled<R> interrupted = interrupted(
                    Attempt.Origin.WAITING, number, false, null);
            if (interrupted != null) {
                return interrupted;
            }

            long before = clock.getAsLong();
            if (number > 1 && reached(before, acquisitionDeadline)) {
                return new WaitOutcome.TimeoutBetweenObservations<>(
                        started, before, lastUnsatisfied);
            }

            Attempt<R> observed = evaluate(source, condition, number);
            long completed = observed.completedNanos();
            switch (observed) {
                case Attempt.Uncontrolled<R> failure ->
                        { return failure; }
                case Attempt.Satisfied<R> satisfied -> {
                    if (reached(completed, acquisitionDeadline)) {
                        return new WaitOutcome.LateSatisfiedTimeout<>(
                                started, satisfied);
                    }
                    return stabilize(source, condition, started, satisfied);
                }
                case Attempt.Unsatisfied<R> unsatisfied -> {
                    if (reached(completed, acquisitionDeadline)) {
                        return new WaitOutcome.LateUnsatisfiedTimeout<>(
                                started, unsatisfied);
                    }
                    lastUnsatisfied = unsatisfied;
                }
            }

            long delay = min(config.everyNanos(),
                    remaining(completed, acquisitionDeadline));
            Attempt.Uncontrolled<R> parked =
                    parkUntil(after(completed, delay), number + 1);
            if (parked != null) {
                return parked;
            }
            number++;
        }
    }

    private <S, R> WaitOutcome<R> stabilize(Source<S> source,
            RuntimeCondition<S, R> condition, long started,
            Attempt.Satisfied<R> acquired) {
        long acquiredAt = acquired.completedNanos();
        if (config.stableForNanos() == 0) {
            return acquired;
        }

        long stabilityDeadline = after(acquiredAt, config.stableForNanos());
        long number = acquired.number();
        long completed = acquiredAt;

        for (;;) {
            long delay = min(config.everyNanos(),
                    remaining(completed, stabilityDeadline));
            Attempt.Uncontrolled<R> parked =
                    parkUntil(after(completed, delay), number + 1);
            if (parked != null) {
                return parked;
            }
            number++;

            Attempt.Uncontrolled<R> interrupted = interrupted(
                    Attempt.Origin.WAITING, number, false, null);
            if (interrupted != null) {
                return interrupted;
            }

            Attempt<R> observed = evaluate(source, condition, number);
            completed = observed.completedNanos();
            switch (observed) {
                case Attempt.Uncontrolled<R> failure ->
                        { return failure; }
                case Attempt.Unsatisfied<R> unsatisfied ->
                        { return new WaitOutcome.StabilityLoss<>(
                                started, acquiredAt, unsatisfied); }
                case Attempt.Satisfied<R> satisfied -> {
                    if (reached(completed, stabilityDeadline)) {
                        return satisfied;
                    }
                }
            }
        }
    }

    private <S, R> Attempt<R> evaluate(Source<S> source,
            RuntimeCondition<S, R> condition, long number) {
        S actual;
        try {
            actual = source.get();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (InterruptedException interrupted) {
            return interrupted(Attempt.Origin.SOURCE, interrupted,
                    number, false, null);
        } catch (Throwable uncontrolled) {
            return new Attempt.Uncontrolled.BeforeObservation<>(
                    Attempt.Origin.SOURCE, uncontrolled, number,
                    clock.getAsLong());
        }

        Attempt.Uncontrolled<R> interrupted = interrupted(
                Attempt.Origin.SOURCE, number, true, actual);
        if (interrupted != null) {
            return interrupted;
        }

        Evaluation<R> evaluation;
        try {
            evaluation = condition.evaluate(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (InterruptedException conditionInterrupted) {
            return interrupted(Attempt.Origin.CONDITION, conditionInterrupted,
                    number, true, actual);
        } catch (Throwable uncontrolled) {
            return new Attempt.Uncontrolled.AfterObservation<>(
                    Attempt.Origin.CONDITION, actual, uncontrolled, number,
                    clock.getAsLong());
        }

        interrupted = interrupted(
                Attempt.Origin.CONDITION, number, true, actual);
        if (interrupted != null) {
            return interrupted;
        }

        long completed = clock.getAsLong();
        if (evaluation == null) {
            return new Attempt.Uncontrolled.AfterObservation<>(
                    Attempt.Origin.CONDITION, actual,
                    new NullPointerException(
                            "condition returned null Evaluation"),
                    number, completed);
        }
        return switch (evaluation.status()) {
            case SATISFIED -> new Attempt.Satisfied<>(
                    actual, evaluation.result(), number, completed);
            case UNSATISFIED -> new Attempt.Unsatisfied<>(actual,
                    evaluation.mismatch(), evaluation.assertionCause(),
                    number, completed);
            case UNCONTROLLED -> new Attempt.Uncontrolled.AfterObservation<>(
                    Attempt.Origin.CONDITION, actual,
                    evaluation.uncontrolledCause(), number, completed);
        };
    }

    private <R> Attempt.Uncontrolled<R> parkUntil(
            long target, long nextNumber) {
        long remaining;
        while ((remaining = remaining(clock.getAsLong(), target)) > 0) {
            try {
                parker.accept(remaining);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable uncontrolled) {
                return new Attempt.Uncontrolled.BeforeObservation<>(
                        Attempt.Origin.WAITING, uncontrolled, nextNumber,
                        clock.getAsLong());
            }
            Attempt.Uncontrolled<R> interrupted = interrupted(
                    Attempt.Origin.WAITING, nextNumber, false, null);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return null;
    }

    private <R> Attempt.Uncontrolled<R> interrupted(Attempt.Origin origin,
            long number, boolean hasActual, Object actual) {
        if (!currentThread().isInterrupted()) {
            return null;
        }
        return interrupted(origin, new InterruptedException(
                        "caller thread interrupt flag was set"),
                number, hasActual, actual);
    }

    private <R> Attempt.Uncontrolled<R> interrupted(Attempt.Origin origin,
            InterruptedException interrupted, long number,
            boolean hasActual, Object actual) {
        currentThread().interrupt();
        long completed = clock.getAsLong();
        return hasActual
                ? new Attempt.Uncontrolled.AfterObservation<>(origin, actual,
                        interrupted, number, completed)
                : new Attempt.Uncontrolled.BeforeObservation<>(origin,
                        interrupted, number, completed);
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
