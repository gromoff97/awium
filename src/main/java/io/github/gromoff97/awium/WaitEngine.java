package io.github.gromoff97.awium;

import java.util.Objects;

@SuppressWarnings("removal")
final class WaitEngine {

    private final WaitConfig config;
    private final NanoClock clock;
    private final Parker parker;
    private final InterruptGuard interruptGuard;

    WaitEngine(
            WaitConfig config,
            NanoClock clock,
            Parker parker,
            InterruptGuard interruptGuard) {
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
        this.parker = Objects.requireNonNull(parker);
        this.interruptGuard = Objects.requireNonNull(interruptGuard);
    }

    <S, R> WaitOutcome<R> waitFor(ObservationEvaluator<S, R> evaluator) {
        Objects.requireNonNull(evaluator);
        long started = clock.nanoTime();
        long acquisitionDeadline = Deadline.after(started, config.upToNanos());
        WaitOutcome.LastObservation lastUnsatisfied = null;
        long attempt = 1;

        for (;;) {
            ObservationOutcome<R> interrupted =
                    interruptGuard.checkWaiting(attempt);
            if (interrupted != null) {
                return WaitOutcome.uncontrolled(interrupted);
            }

            long before = clock.nanoTime();
            if (attempt > 1 && Deadline.reached(before, acquisitionDeadline)) {
                return WaitOutcome.timeoutBetween(
                        started, before, lastUnsatisfied);
            }

            ObservationOutcome<R> observed = evaluator.evaluate(attempt);
            long completed = clock.nanoTime();
            if (observed.isUncontrolled()) {
                return WaitOutcome.uncontrolled(observed);
            }
            if (Deadline.reached(completed, acquisitionDeadline)) {
                return observed.isSatisfied()
                        ? WaitOutcome.lateSatisfied(started, completed, observed)
                        : WaitOutcome.lateUnsatisfied(started, completed, observed);
            }
            if (observed.isSatisfied()) {
                return stabilize(evaluator, started, observed, completed);
            }

            lastUnsatisfied = new WaitOutcome.LastObservation(
                    observed.attempt(), completed, observed.mismatch(),
                    observed.assertionCause());
            long delay = Math.min(config.everyNanos(),
                    Deadline.remaining(completed, acquisitionDeadline));
            ObservationOutcome<R> parked = parkUntil(
                    Deadline.after(completed, delay), attempt + 1);
            if (parked != null) {
                return WaitOutcome.uncontrolled(parked);
            }
            attempt++;
        }
    }

    private <S, R> WaitOutcome<R> stabilize(
            ObservationEvaluator<S, R> evaluator,
            long started,
            ObservationOutcome<R> acquired,
            long acquiredAt) {
        if (config.stableForNanos() == 0) {
            return WaitOutcome.success(started, acquiredAt, acquiredAt, acquired);
        }

        long stabilityDeadline = Deadline.after(
                acquiredAt, config.stableForNanos());
        long attempt = acquired.attempt();
        long completed = acquiredAt;

        for (;;) {
            long delay = Math.min(config.everyNanos(),
                    Deadline.remaining(completed, stabilityDeadline));
            ObservationOutcome<R> parked = parkUntil(
                    Deadline.after(completed, delay), attempt + 1);
            if (parked != null) {
                return WaitOutcome.uncontrolled(parked);
            }
            attempt++;

            ObservationOutcome<R> interrupted =
                    interruptGuard.checkWaiting(attempt);
            if (interrupted != null) {
                return WaitOutcome.uncontrolled(interrupted);
            }

            ObservationOutcome<R> observed = evaluator.evaluate(attempt);
            completed = clock.nanoTime();
            if (observed.isUncontrolled()) {
                return WaitOutcome.uncontrolled(observed);
            }
            if (observed.isUnsatisfied()) {
                return WaitOutcome.stabilityLoss(
                        started, acquiredAt, completed, observed);
            }
            if (Deadline.reached(completed, stabilityDeadline)) {
                return WaitOutcome.success(
                        started, acquiredAt, completed, observed);
            }
        }
    }

    private <R> ObservationOutcome<R> parkUntil(
            long target, long nextAttempt) {
        long remaining;
        while ((remaining = Deadline.remaining(clock.nanoTime(), target)) > 0) {
            try {
                parker.parkNanos(remaining);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable uncontrolled) {
                return ObservationOutcome.uncontrolled(
                        ObservationOutcome.Origin.WAITING,
                        uncontrolled, nextAttempt);
            }
            ObservationOutcome<R> interrupted =
                    interruptGuard.checkWaiting(nextAttempt);
            if (interrupted != null) {
                return interrupted;
            }
        }
        return null;
    }
}
