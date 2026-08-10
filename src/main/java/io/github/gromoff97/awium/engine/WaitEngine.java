package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.sources.Source;

import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

@SuppressWarnings("removal")
public final class WaitEngine {

    private static final String FLAG_MESSAGE =
            "caller thread interrupt flag was set";

    private final WaitConfiguration config;
    private final LongSupplier clock;
    private final LongConsumer parker;

    public WaitEngine(WaitConfiguration config, LongSupplier clock,
            LongConsumer parker) {
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
        this.parker = Objects.requireNonNull(parker);
    }

    public <S, R> WaitOutcome<R> waitFor(
            Source<S> source, RuntimeCondition<S, R> condition) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(condition);
        long started = clock.getAsLong();
        long acquisitionDeadline = after(started, config.upToNanos());
        Attempt<R> lastUnsatisfied = null;
        long number = 1;

        for (;;) {
            Attempt<R> interrupted = interrupted(
                    Attempt.Origin.WAITING, number, false, null);
            if (interrupted != null) {
                return WaitOutcome.uncontrolled(interrupted);
            }

            long before = clock.getAsLong();
            if (number > 1 && reached(before, acquisitionDeadline)) {
                return WaitOutcome.timeoutBetween(
                        started, before, lastUnsatisfied);
            }

            Attempt<R> observed = evaluate(source, condition, number);
            long completed = observed.completedNanos();
            if (observed.status() == Attempt.Status.UNCONTROLLED) {
                return WaitOutcome.uncontrolled(observed);
            }
            if (reached(completed, acquisitionDeadline)) {
                return observed.status() == Attempt.Status.SATISFIED
                        ? WaitOutcome.lateSatisfied(started, completed, observed)
                        : WaitOutcome.lateUnsatisfied(started, completed, observed);
            }
            if (observed.status() == Attempt.Status.SATISFIED) {
                return stabilize(source, condition, started, observed);
            }

            lastUnsatisfied = observed;
            long delay = Math.min(config.everyNanos(),
                    remaining(completed, acquisitionDeadline));
            Attempt<R> parked = parkUntil(after(completed, delay), number + 1);
            if (parked != null) {
                return WaitOutcome.uncontrolled(parked);
            }
            number++;
        }
    }

    private <S, R> WaitOutcome<R> stabilize(Source<S> source,
            RuntimeCondition<S, R> condition, long started,
            Attempt<R> acquired) {
        long acquiredAt = acquired.completedNanos();
        if (config.stableForNanos() == 0) {
            return WaitOutcome.success(
                    started, acquiredAt, acquiredAt, acquired);
        }

        long stabilityDeadline = after(acquiredAt, config.stableForNanos());
        long number = acquired.number();
        long completed = acquiredAt;

        for (;;) {
            long delay = Math.min(config.everyNanos(),
                    remaining(completed, stabilityDeadline));
            Attempt<R> parked = parkUntil(after(completed, delay), number + 1);
            if (parked != null) {
                return WaitOutcome.uncontrolled(parked);
            }
            number++;

            Attempt<R> interrupted = interrupted(
                    Attempt.Origin.WAITING, number, false, null);
            if (interrupted != null) {
                return WaitOutcome.uncontrolled(interrupted);
            }

            Attempt<R> observed = evaluate(source, condition, number);
            completed = observed.completedNanos();
            if (observed.status() == Attempt.Status.UNCONTROLLED) {
                return WaitOutcome.uncontrolled(observed);
            }
            if (observed.status() == Attempt.Status.UNSATISFIED) {
                return WaitOutcome.stabilityLoss(
                        started, acquiredAt, completed, observed);
            }
            if (reached(completed, stabilityDeadline)) {
                return WaitOutcome.success(
                        started, acquiredAt, completed, observed);
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
            return Attempt.uncontrolled(Attempt.Origin.SOURCE, false, null,
                    uncontrolled, number, clock.getAsLong());
        }

        Attempt<R> interrupted = interrupted(
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
            return Attempt.uncontrolled(Attempt.Origin.CONDITION, true, actual,
                    uncontrolled, number, clock.getAsLong());
        }

        interrupted = interrupted(
                Attempt.Origin.CONDITION, number, true, actual);
        if (interrupted != null) {
            return interrupted;
        }

        long completed = clock.getAsLong();
        if (evaluation == null) {
            return Attempt.uncontrolled(Attempt.Origin.CONDITION, true, actual,
                    new NullPointerException(
                            "condition returned null Evaluation"),
                    number, completed);
        }
        return switch (evaluation.status()) {
            case SATISFIED -> Attempt.satisfied(
                    actual, evaluation.result(), number, completed);
            case UNSATISFIED -> Attempt.unsatisfied(actual,
                    evaluation.mismatch(), evaluation.assertionCause(),
                    number, completed);
            case UNCONTROLLED -> Attempt.uncontrolled(
                    Attempt.Origin.CONDITION, true, actual,
                    evaluation.uncontrolledCause(), number, completed);
        };
    }

    private <R> Attempt<R> parkUntil(long target, long nextNumber) {
        long remaining;
        while ((remaining = remaining(clock.getAsLong(), target)) > 0) {
            try {
                parker.accept(remaining);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable uncontrolled) {
                return Attempt.uncontrolled(Attempt.Origin.WAITING,
                        false, null, uncontrolled, nextNumber,
                        clock.getAsLong());
            }
            Attempt<R> interrupted = interrupted(
                    Attempt.Origin.WAITING, nextNumber, false, null);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return null;
    }

    private <R> Attempt<R> interrupted(Attempt.Origin origin, long number,
            boolean hasActual, Object actual) {
        if (!Thread.currentThread().isInterrupted()) {
            return null;
        }
        return interrupted(origin, new InterruptedException(FLAG_MESSAGE),
                number, hasActual, actual);
    }

    private <R> Attempt<R> interrupted(Attempt.Origin origin,
            InterruptedException interrupted, long number,
            boolean hasActual, Object actual) {
        Thread.currentThread().interrupt();
        return Attempt.uncontrolled(origin, hasActual, actual,
                Objects.requireNonNull(interrupted), number, clock.getAsLong());
    }

    private static long after(long now, long durationNanos) {
        return now + durationNanos;
    }

    private static boolean reached(long now, long deadline) {
        return now - deadline >= 0;
    }

    private static long remaining(long now, long deadline) {
        long value = deadline - now;
        return value <= 0 ? 0 : value;
    }
}
