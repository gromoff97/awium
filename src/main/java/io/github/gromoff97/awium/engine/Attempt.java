package io.github.gromoff97.awium.engine;

import static java.util.Objects.requireNonNull;

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
        requireNonNull(status);
        if (number <= 0) {
            throw new IllegalArgumentException(
                    "attempt number must be greater than zero");
        }
        switch (status) {
            case SATISFIED -> {
                if (origin != null || cause != null || !hasActual
                        || mismatch != null || assertionCause != null) {
                    throw new IllegalArgumentException(
                            "invalid satisfied attempt state");
                }
            }
            case UNSATISFIED -> {
                requireNonNull(mismatch);
                if (origin != null || cause != null || !hasActual
                        || result != null) {
                    throw new IllegalArgumentException(
                            "invalid unsatisfied attempt state");
                }
            }
            case UNCONTROLLED -> {
                requireNonNull(origin);
                requireNonNull(cause);
                if (result != null || mismatch != null
                        || assertionCause != null
                        || !hasActual && actual != null) {
                    throw new IllegalArgumentException(
                            "invalid uncontrolled attempt state");
                }
            }
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
