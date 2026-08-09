package io.github.gromoff97.assertility;

import java.util.Objects;

final class InterruptGuard {

    private static final String FLAG_MESSAGE =
            "caller thread interrupt flag was set";

    <R> ObservationOutcome<R> checkWaiting(long attempt) {
        return check(ObservationOutcome.Origin.WAITING, attempt, false, null);
    }

    <R> ObservationOutcome<R> checkSource(long attempt, Object actual) {
        return check(ObservationOutcome.Origin.SOURCE, attempt, true, actual);
    }

    <R> ObservationOutcome<R> checkCondition(long attempt, Object actual) {
        return check(ObservationOutcome.Origin.CONDITION, attempt, true, actual);
    }

    <R> ObservationOutcome<R> fromThrown(
            ObservationOutcome.Origin origin,
            InterruptedException interrupted,
            long attempt) {
        return interrupted(origin, interrupted, attempt, false, null);
    }

    <R> ObservationOutcome<R> fromThrown(
            ObservationOutcome.Origin origin,
            InterruptedException interrupted,
            long attempt,
            Object actual) {
        return interrupted(origin, interrupted, attempt, true, actual);
    }

    private <R> ObservationOutcome<R> check(
            ObservationOutcome.Origin origin,
            long attempt,
            boolean hasActual,
            Object actual) {
        if (!Thread.currentThread().isInterrupted()) {
            return null;
        }
        return interrupted(origin, new InterruptedException(FLAG_MESSAGE),
                attempt, hasActual, actual);
    }

    private <R> ObservationOutcome<R> interrupted(
            ObservationOutcome.Origin origin,
            InterruptedException interrupted,
            long attempt,
            boolean hasActual,
            Object actual) {
        Thread.currentThread().interrupt();
        return hasActual
                ? ObservationOutcome.uncontrolled(origin,
                        Objects.requireNonNull(interrupted), attempt, actual)
                : ObservationOutcome.uncontrolled(origin,
                        Objects.requireNonNull(interrupted), attempt);
    }
}
