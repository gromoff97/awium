package io.github.gromoff97.awium.internal.engine;

import java.util.Objects;

public final class Interrupts {

    private static final String FLAG_MESSAGE =
            "caller thread interrupt flag was set";

    public <R> AttemptResult<R> checkWaiting(long attempt) {
        return check(AttemptResult.Origin.WAITING, attempt, false, null);
    }

    public <R> AttemptResult<R> checkSource(long attempt, Object actual) {
        return check(AttemptResult.Origin.SOURCE, attempt, true, actual);
    }

    public <R> AttemptResult<R> checkCondition(long attempt, Object actual) {
        return check(AttemptResult.Origin.CONDITION, attempt, true, actual);
    }

    public <R> AttemptResult<R> fromThrown(AttemptResult.Origin origin,
            InterruptedException interrupted, long attempt) {
        return interrupted(origin, interrupted, attempt, false, null);
    }

    public <R> AttemptResult<R> fromThrown(AttemptResult.Origin origin,
            InterruptedException interrupted, long attempt, Object actual) {
        return interrupted(origin, interrupted, attempt, true, actual);
    }

    private <R> AttemptResult<R> check(AttemptResult.Origin origin,
            long attempt, boolean hasActual, Object actual) {
        if (!Thread.currentThread().isInterrupted()) {
            return null;
        }
        return interrupted(origin, new InterruptedException(FLAG_MESSAGE),
                attempt, hasActual, actual);
    }

    private <R> AttemptResult<R> interrupted(AttemptResult.Origin origin,
            InterruptedException interrupted, long attempt,
            boolean hasActual, Object actual) {
        Thread.currentThread().interrupt();
        return hasActual
                ? AttemptResult.uncontrolled(origin,
                        Objects.requireNonNull(interrupted), attempt, actual)
                : AttemptResult.uncontrolled(origin,
                        Objects.requireNonNull(interrupted), attempt);
    }
}
