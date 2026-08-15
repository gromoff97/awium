package io.github.gromoff97.awium.engine;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider.CheckedFunction;
import io.github.gromoff97.awium.sources.Source;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

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
public record WaitEngine(WaitConfiguration configuration, LongSupplier clock, LongConsumer parker) {

    public <S, R> WaitOutcome<R> waitFor(Source<? extends S> source,
            CheckedFunction<? super S, ? extends Evaluation<? extends R>> evaluator) {
        configuration.validatePair();
        long started = clock.getAsLong();
        long phaseDeadline = after(started, configuration.upToNanos());
        Unsatisfied<R> lastUnsatisfied = null;
        boolean stabilizing = false;
        long acquiredAt = 0;
        long completed = started;

        for (long number = 1;; number++) {
            if (number > 1) {
                long delay = min(configuration.everyNanos(), remaining(completed, phaseDeadline));
                Uncontrolled<R> parked = parkUntil(after(completed, delay), number);
                if (parked != null) {
                    return parked;
                }
            }

            Uncontrolled<R> interrupted = interruptedBefore(WAITING, number);
            if (interrupted != null) {
                return interrupted;
            }

            if (!stabilizing) {
                long before = clock.getAsLong();
                if (number > 1 && reached(before, phaseDeadline)) {
                    return new TimeoutBetweenObservations<>(started, before, lastUnsatisfied);
                }
            }

            Attempt<R> observed = observe(source, evaluator, number);
            completed = observed.completedNanos();
            if (observed instanceof Uncontrolled<R> failure) {
                return failure;
            }

            if (stabilizing) {
                if (observed instanceof Unsatisfied<R> unsatisfied) {
                    return new StabilityLoss<>(started, acquiredAt, unsatisfied);
                }
                Satisfied<R> satisfied = (Satisfied<R>) observed;
                if (reached(completed, phaseDeadline)) {
                    return satisfied;
                }
                continue;
            }

            if (observed instanceof Satisfied<R> satisfied) {
                if (reached(completed, phaseDeadline)) {
                    return new LateSatisfiedTimeout<>(started, satisfied);
                }
                if (configuration.stableForNanos() == 0) {
                    return satisfied;
                }
                stabilizing = true;
                acquiredAt = completed;
                phaseDeadline = after(completed, configuration.stableForNanos());
            } else {
                Unsatisfied<R> unsatisfied = (Unsatisfied<R>) observed;
                if (reached(completed, phaseDeadline)) {
                    return new LateUnsatisfiedTimeout<>(started, unsatisfied);
                }
                lastUnsatisfied = unsatisfied;
            }
        }
    }

    private <S, R> Attempt<R> observe(Source<? extends S> source,
            CheckedFunction<? super S, ? extends Evaluation<? extends R>> evaluator, long number) {
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

        Evaluation<? extends R> evaluation;
        try {
            evaluation = evaluator.apply(actual);
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

    private <R> Uncontrolled<R> interruptedAfter(Origin origin, long number, Object actual) {
        return interrupted()
                ? interruptedAfter(origin, new InterruptedException("caller thread interrupt flag was set"), number, actual)
                : null;
    }

    private static boolean interrupted() {
        return currentThread().isInterrupted();
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
