package io.github.gromoff97.awium.engine;

import static java.util.Objects.requireNonNull;

public record WaitOutcome<R>(
        Kind kind,
        long startedNanos,
        long acquiredNanos,
        long completedNanos,
        Attempt<R> attempt) {

    public WaitOutcome {
        requireNonNull(kind);
        requireNonNull(attempt);
        Attempt.Status expected = switch (kind) {
            case SUCCESS, LATE_SATISFIED_TIMEOUT -> Attempt.Status.SATISFIED;
            case TIMEOUT_BETWEEN_OBSERVATIONS, LATE_UNSATISFIED_TIMEOUT,
                    STABILITY_LOSS -> Attempt.Status.UNSATISFIED;
            case UNCONTROLLED -> Attempt.Status.UNCONTROLLED;
        };
        if (attempt.status() != expected) {
            throw new IllegalArgumentException(
                    kind + " outcome requires a " + expected + " attempt");
        }
    }

    public enum Kind {
        SUCCESS,
        TIMEOUT_BETWEEN_OBSERVATIONS,
        LATE_UNSATISFIED_TIMEOUT,
        LATE_SATISFIED_TIMEOUT,
        STABILITY_LOSS,
        UNCONTROLLED
    }

    public static <R> WaitOutcome<R> success(long startedNanos,
            long acquiredNanos, long completedNanos, Attempt<R> attempt) {
        return new WaitOutcome<>(Kind.SUCCESS, startedNanos, acquiredNanos,
                completedNanos, attempt);
    }

    public static <R> WaitOutcome<R> timeoutBetween(long startedNanos,
            long completedNanos, Attempt<R> attempt) {
        return new WaitOutcome<>(Kind.TIMEOUT_BETWEEN_OBSERVATIONS,
                startedNanos, 0, completedNanos, attempt);
    }

    public static <R> WaitOutcome<R> lateUnsatisfied(long startedNanos,
            long completedNanos, Attempt<R> attempt) {
        return new WaitOutcome<>(Kind.LATE_UNSATISFIED_TIMEOUT,
                startedNanos, 0, completedNanos, attempt);
    }

    public static <R> WaitOutcome<R> lateSatisfied(long startedNanos,
            long completedNanos, Attempt<R> attempt) {
        return new WaitOutcome<>(Kind.LATE_SATISFIED_TIMEOUT,
                startedNanos, 0, completedNanos, attempt);
    }

    public static <R> WaitOutcome<R> stabilityLoss(long startedNanos,
            long acquiredNanos, long completedNanos, Attempt<R> attempt) {
        return new WaitOutcome<>(Kind.STABILITY_LOSS, startedNanos,
                acquiredNanos, completedNanos, attempt);
    }

    public static <R> WaitOutcome<R> uncontrolled(Attempt<R> attempt) {
        return new WaitOutcome<>(Kind.UNCONTROLLED, 0, 0, 0, attempt);
    }

    public R result() {
        return attempt.result();
    }

    public long completedAttempts() {
        return attempt.number();
    }
}
