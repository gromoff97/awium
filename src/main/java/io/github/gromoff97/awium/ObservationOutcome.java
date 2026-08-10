package io.github.gromoff97.awium;

record ObservationOutcome<R>(
        Status status,
        boolean hasActual,
        Object actual,
        R result,
        String mismatch,
        AssertionError assertionCause,
        Origin origin,
        Throwable cause,
        long attempt) {

    enum Status { SATISFIED, UNSATISFIED, UNCONTROLLED }

    enum Origin { WAITING, SOURCE, CONDITION }

    static <R> ObservationOutcome<R> satisfied(
            Object actual, R result, long attempt) {
        return new ObservationOutcome<>(Status.SATISFIED, true, actual, result,
                null, null, null, null, attempt);
    }

    static <R> ObservationOutcome<R> unsatisfied(
            Object actual, String mismatch, AssertionError assertionCause,
            long attempt) {
        return new ObservationOutcome<>(Status.UNSATISFIED, true, actual, null,
                mismatch, assertionCause, null, null, attempt);
    }

    static <R> ObservationOutcome<R> uncontrolled(
            Origin origin, Throwable cause, long attempt) {
        return new ObservationOutcome<>(Status.UNCONTROLLED, false, null, null,
                null, null, origin, cause, attempt);
    }

    static <R> ObservationOutcome<R> uncontrolled(
            Origin origin, Throwable cause, long attempt, Object actual) {
        return new ObservationOutcome<>(Status.UNCONTROLLED, true, actual, null,
                null, null, origin, cause, attempt);
    }

    boolean isSatisfied() {
        return status == Status.SATISFIED;
    }

    boolean isUnsatisfied() {
        return status == Status.UNSATISFIED;
    }

    boolean isUncontrolled() {
        return status == Status.UNCONTROLLED;
    }
}
