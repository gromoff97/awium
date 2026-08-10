package io.github.gromoff97.awium;

record WaitOutcome<R>(
        Kind kind,
        long startedNanos,
        long acquiredNanos,
        long completedNanos,
        ObservationOutcome<R> observation,
        LastObservation lastObservation) {

    enum Kind {
        SUCCESS,
        TIMEOUT_BETWEEN_OBSERVATIONS,
        LATE_UNSATISFIED_TIMEOUT,
        LATE_SATISFIED_TIMEOUT,
        STABILITY_LOSS,
        UNCONTROLLED
    }

    record LastObservation(
            long attempt,
            long completedNanos,
            String mismatch,
            AssertionError assertionCause) {
    }

    static <R> WaitOutcome<R> success(
            long startedNanos,
            long acquiredNanos,
            long completedNanos,
            ObservationOutcome<R> observation) {
        return new WaitOutcome<>(Kind.SUCCESS, startedNanos, acquiredNanos,
                completedNanos, observation, null);
    }

    static <R> WaitOutcome<R> timeoutBetween(
            long startedNanos,
            long completedNanos,
            LastObservation lastObservation) {
        return new WaitOutcome<>(Kind.TIMEOUT_BETWEEN_OBSERVATIONS,
                startedNanos, 0, completedNanos, null, lastObservation);
    }

    static <R> WaitOutcome<R> lateUnsatisfied(
            long startedNanos,
            long completedNanos,
            ObservationOutcome<R> observation) {
        return new WaitOutcome<>(Kind.LATE_UNSATISFIED_TIMEOUT,
                startedNanos, 0, completedNanos, observation, null);
    }

    static <R> WaitOutcome<R> lateSatisfied(
            long startedNanos,
            long completedNanos,
            ObservationOutcome<R> observation) {
        return new WaitOutcome<>(Kind.LATE_SATISFIED_TIMEOUT,
                startedNanos, 0, completedNanos, observation, null);
    }

    static <R> WaitOutcome<R> stabilityLoss(
            long startedNanos,
            long acquiredNanos,
            long completedNanos,
            ObservationOutcome<R> observation) {
        return new WaitOutcome<>(Kind.STABILITY_LOSS, startedNanos,
                acquiredNanos, completedNanos, observation, null);
    }

    static <R> WaitOutcome<R> uncontrolled(
            ObservationOutcome<R> observation) {
        return new WaitOutcome<>(Kind.UNCONTROLLED, 0, 0, 0,
                observation, null);
    }

    R result() {
        return observation.result();
    }

    long completedAttempts() {
        return kind == Kind.TIMEOUT_BETWEEN_OBSERVATIONS
                ? lastObservation.attempt()
                : observation.attempt();
    }
}
