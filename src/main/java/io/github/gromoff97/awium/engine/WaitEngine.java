package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.sources.Source;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNCONTROLLED;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Origin;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Origin.CONDITION;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Origin.SOURCE;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Origin.WAITING;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Satisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled.AfterObservation;
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
public record WaitEngine(WaitConfiguration config, LongSupplier clock, LongConsumer parker) {

    public <S, R> WaitOutcome<R> waitFor(Source<? extends S> source, RuntimeCondition<S, R> condition) {
        config.validatePair();
        long started = clock.getAsLong();
        long acquisitionDeadline = after(started, config.upToNanos());
        Unsatisfied<R> lastUnsatisfied = null;
        long number = 1;

        for (;;) {
            Uncontrolled<R> interrupted = interruptedBefore(WAITING, number);
            if (interrupted != null) {
                return interrupted;
            }

            long before = clock.getAsLong();
            if (number > 1 && reached(before, acquisitionDeadline)) {
                return new TimeoutBetweenObservations<>(started, before, lastUnsatisfied);
            }

            Attempt<R> observed = evaluate(source, condition, number);
            long completed = observed.completedNanos();
            switch (observed) {
                case Uncontrolled<R> failure -> { return failure; }
                case Satisfied<R> satisfied -> {
                    if (reached(completed, acquisitionDeadline)) {
                        return new LateSatisfiedTimeout<>(started, satisfied);
                    }
                    return stabilize(source, condition, started, satisfied);
                }
                case Unsatisfied<R> unsatisfied -> {
                    if (reached(completed, acquisitionDeadline)) {
                        return new LateUnsatisfiedTimeout<>(started, unsatisfied);
                    }
                    lastUnsatisfied = unsatisfied;
                }
            }

            long delay = min(config.everyNanos(), remaining(completed, acquisitionDeadline));
            Uncontrolled<R> parked = parkUntil(after(completed, delay), number + 1);
            if (parked != null) {
                return parked;
            }
            number++;
        }
    }

    private <S, R> WaitOutcome<R> stabilize(Source<? extends S> source, RuntimeCondition<S, R> condition, long started, Satisfied<R> acquired) {
        long acquiredAt = acquired.completedNanos();
        if (config.stableForNanos() == 0) {
            return acquired;
        }

        long stabilityDeadline = after(acquiredAt, config.stableForNanos());
        long number = acquired.number();
        long completed = acquiredAt;

        for (;;) {
            long delay = min(config.everyNanos(), remaining(completed, stabilityDeadline));
            Uncontrolled<R> parked = parkUntil(after(completed, delay), number + 1);
            if (parked != null) {
                return parked;
            }
            number++;

            Uncontrolled<R> interrupted = interruptedBefore(WAITING, number);
            if (interrupted != null) {
                return interrupted;
            }

            Attempt<R> observed = evaluate(source, condition, number);
            completed = observed.completedNanos();
            switch (observed) {
                case Uncontrolled<R> failure -> { return failure; }
                case Unsatisfied<R> unsatisfied -> { return new StabilityLoss<>(started, acquiredAt, unsatisfied); }
                case Satisfied<R> satisfied -> {
                    if (reached(completed, stabilityDeadline)) {
                        return satisfied;
                    }
                }
            }
        }
    }

    private <S, R> Attempt<R> evaluate(Source<? extends S> source, RuntimeCondition<S, R> condition, long number) {
        S actual;
        try {
            actual = source.get();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (InterruptedException interrupted) {
            return interruptedBefore(SOURCE, interrupted, number);
        } catch (Throwable uncontrolled) {
            return new BeforeObservation<>(SOURCE, uncontrolled, number, clock.getAsLong());
        }

        Uncontrolled<R> interrupted = interruptedAfter(SOURCE, number, actual);
        if (interrupted != null) {
            return interrupted;
        }

        Evaluation<R> evaluation;
        try {
            evaluation = condition.evaluator().apply(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (InterruptedException conditionInterrupted) {
            return interruptedAfter(CONDITION, conditionInterrupted, number, actual);
        } catch (Throwable uncontrolled) {
            return new AfterObservation<>(CONDITION, actual, uncontrolled, number, clock.getAsLong());
        }

        if (evaluation == null || evaluation.status() != UNCONTROLLED) {
            interrupted = interruptedAfter(CONDITION, number, actual);
            if (interrupted != null) {
                return interrupted;
            }
        }

        long completed = clock.getAsLong();
        if (evaluation == null) {
            return new AfterObservation<>(CONDITION, actual, new NullPointerException("condition returned null Evaluation"), number, completed);
        }
        return switch (evaluation.status()) {
            case SATISFIED -> new Satisfied<>(actual, evaluation.result(), number, completed);
            case UNSATISFIED -> new Unsatisfied<>(actual, evaluation.mismatch(), evaluation.assertionCause(), number, completed);
            case UNCONTROLLED -> {
                Throwable cause = evaluation.uncontrolledCause();
                if (cause instanceof Error fatal && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
                    throw fatal;
                }
                yield cause instanceof InterruptedException interruption
                        ? interruptedAfter(CONDITION, interruption, number, actual)
                        : new AfterObservation<>(CONDITION, actual, cause, number, completed);
            }
        };
    }

    private <R> Uncontrolled<R> parkUntil(long target, long nextNumber) {
        long remaining;
        while ((remaining = remaining(clock.getAsLong(), target)) > 0) {
            try {
                parker.accept(remaining);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable uncontrolled) {
                if (uncontrolled instanceof InterruptedException interruption) {
                    return interruptedBefore(WAITING, interruption, nextNumber);
                }
                return new BeforeObservation<>(WAITING, uncontrolled, nextNumber, clock.getAsLong());
            }
            Uncontrolled<R> interrupted = interruptedBefore(WAITING, nextNumber);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return null;
    }

    private <R> Uncontrolled<R> interruptedBefore(Origin origin, long number) {
        return interrupted(() -> interruptedBefore(origin, new InterruptedException("caller thread interrupt flag was set"), number));
    }

    private <R> Uncontrolled<R> interruptedAfter(Origin origin, long number, Object actual) {
        return interrupted(() -> interruptedAfter(origin, new InterruptedException("caller thread interrupt flag was set"), number, actual));
    }

    private <R> Uncontrolled<R> interrupted(Supplier<Uncontrolled<R>> attempt) {
        return currentThread().isInterrupted() ? attempt.get() : null;
    }

    private <R> Uncontrolled<R> interruptedBefore(Origin origin, InterruptedException interrupted, long number) {
        restoreInterrupt();
        return new BeforeObservation<>(origin, interrupted, number, clock.getAsLong());
    }

    private <R> Uncontrolled<R> interruptedAfter(Origin origin, InterruptedException interrupted, long number, Object actual) {
        restoreInterrupt();
        return new AfterObservation<>(origin, actual, interrupted, number, clock.getAsLong());
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
