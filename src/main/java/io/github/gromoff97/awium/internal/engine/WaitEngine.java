package io.github.gromoff97.awium.internal.engine;

import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

@SuppressWarnings("removal")
public final class WaitEngine {

    private final WaitConfiguration config;
    private final LongSupplier clock;
    private final LongConsumer parker;
    private final Interrupts interrupts;

    public WaitEngine(WaitConfiguration config, LongSupplier clock,
            LongConsumer parker, Interrupts interrupts) {
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
        this.parker = Objects.requireNonNull(parker);
        this.interrupts = Objects.requireNonNull(interrupts);
    }

    public <R> WaitResult<R> waitFor(
            LongFunction<AttemptResult<R>> evaluator) {
        Objects.requireNonNull(evaluator);
        long started = clock.getAsLong();
        long acquisitionDeadline = after(started, config.upToNanos());
        WaitResult.LastObservation lastUnsatisfied = null;
        long attempt = 1;

        for (;;) {
            AttemptResult<R> interrupted = interrupts.checkWaiting(attempt);
            if (interrupted != null) {
                return WaitResult.uncontrolled(interrupted);
            }

            long before = clock.getAsLong();
            if (attempt > 1 && reached(before, acquisitionDeadline)) {
                return WaitResult.timeoutBetween(
                        started, before, lastUnsatisfied);
            }

            AttemptResult<R> observed = evaluator.apply(attempt);
            long completed = clock.getAsLong();
            if (observed.isUncontrolled()) {
                return WaitResult.uncontrolled(observed);
            }
            if (reached(completed, acquisitionDeadline)) {
                return observed.isSatisfied()
                        ? WaitResult.lateSatisfied(started, completed, observed)
                        : WaitResult.lateUnsatisfied(started, completed, observed);
            }
            if (observed.isSatisfied()) {
                return stabilize(evaluator, started, observed, completed);
            }

            lastUnsatisfied = new WaitResult.LastObservation(
                    observed.attempt(), completed, observed.mismatch(),
                    observed.assertionCause());
            long delay = Math.min(config.everyNanos(),
                    remaining(completed, acquisitionDeadline));
            AttemptResult<R> parked = parkUntil(
                    after(completed, delay), attempt + 1);
            if (parked != null) {
                return WaitResult.uncontrolled(parked);
            }
            attempt++;
        }
    }

    private <R> WaitResult<R> stabilize(
            LongFunction<AttemptResult<R>> evaluator,
            long started, AttemptResult<R> acquired, long acquiredAt) {
        if (config.stableForNanos() == 0) {
            return WaitResult.success(
                    started, acquiredAt, acquiredAt, acquired);
        }

        long stabilityDeadline = after(acquiredAt, config.stableForNanos());
        long attempt = acquired.attempt();
        long completed = acquiredAt;

        for (;;) {
            long delay = Math.min(config.everyNanos(),
                    remaining(completed, stabilityDeadline));
            AttemptResult<R> parked = parkUntil(
                    after(completed, delay), attempt + 1);
            if (parked != null) {
                return WaitResult.uncontrolled(parked);
            }
            attempt++;

            AttemptResult<R> interrupted = interrupts.checkWaiting(attempt);
            if (interrupted != null) {
                return WaitResult.uncontrolled(interrupted);
            }

            AttemptResult<R> observed = evaluator.apply(attempt);
            completed = clock.getAsLong();
            if (observed.isUncontrolled()) {
                return WaitResult.uncontrolled(observed);
            }
            if (observed.isUnsatisfied()) {
                return WaitResult.stabilityLoss(
                        started, acquiredAt, completed, observed);
            }
            if (reached(completed, stabilityDeadline)) {
                return WaitResult.success(
                        started, acquiredAt, completed, observed);
            }
        }
    }

    private <R> AttemptResult<R> parkUntil(long target, long nextAttempt) {
        long remaining;
        while ((remaining = remaining(clock.getAsLong(), target)) > 0) {
            try {
                parker.accept(remaining);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable uncontrolled) {
                return AttemptResult.uncontrolled(AttemptResult.Origin.WAITING,
                        uncontrolled, nextAttempt);
            }
            AttemptResult<R> interrupted =
                    interrupts.checkWaiting(nextAttempt);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return null;
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
