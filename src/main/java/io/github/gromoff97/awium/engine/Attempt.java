package io.github.gromoff97.awium.engine;

import java.util.Objects;

public record Attempt<R>(
        Status status,
        Origin origin,
        boolean hasActual,
        Object actual,
        R result,
        String mismatch,
        AssertionError assertionCause,
        Throwable cause,
        long number,
        long completedNanos) {

    public Attempt {
        Objects.requireNonNull(status);
        if (number <= 0) {
            throw new IllegalArgumentException(
                    "attempt number must be greater than zero");
        }
        if (status == Status.UNSATISFIED) {
            Objects.requireNonNull(mismatch);
        }
        if (status == Status.UNCONTROLLED) {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(cause);
        } else if (origin != null || cause != null) {
            throw new IllegalArgumentException(
                    "controlled attempts must not have origin or cause");
        }
    }

    public enum Status { SATISFIED, UNSATISFIED, UNCONTROLLED }

    public enum Origin { WAITING, SOURCE, CONDITION }

    public static <R> Attempt<R> satisfied(
            Object actual, R result, long number, long completedNanos) {
        return new Attempt<>(Status.SATISFIED, null, true, actual, result,
                null, null, null, number, completedNanos);
    }

    public static <R> Attempt<R> unsatisfied(Object actual, String mismatch,
            AssertionError assertionCause, long number, long completedNanos) {
        return new Attempt<>(Status.UNSATISFIED, null, true, actual, null,
                mismatch, assertionCause, null, number, completedNanos);
    }

    public static <R> Attempt<R> uncontrolled(Origin origin,
            boolean hasActual, Object actual, Throwable cause, long number,
            long completedNanos) {
        return new Attempt<>(Status.UNCONTROLLED, origin, hasActual, actual,
                null, null, null, cause, number, completedNanos);
    }
}
