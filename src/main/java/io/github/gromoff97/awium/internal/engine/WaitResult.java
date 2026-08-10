package io.github.gromoff97.awium.internal.engine;

import java.util.Objects;

public sealed interface WaitResult<R>
        permits WaitResult.Success, WaitResult.TimeoutBetweenAttempts,
                WaitResult.LateUnsatisfied, WaitResult.LateSatisfied,
                WaitResult.StabilityLost, WaitResult.Uncontrolled {

    enum Kind {
        SUCCESS,
        TIMEOUT_BETWEEN_OBSERVATIONS,
        LATE_UNSATISFIED_TIMEOUT,
        LATE_SATISFIED_TIMEOUT,
        STABILITY_LOSS,
        UNCONTROLLED
    }

    record LastObservation(long attempt, long completedNanos, String mismatch,
            AssertionError assertionCause) {

        public LastObservation {
            Objects.requireNonNull(mismatch);
        }
    }

    Kind kind();

    default long startedNanos() {
        return 0;
    }

    default long acquiredNanos() {
        return 0;
    }

    default long completedNanos() {
        return 0;
    }

    default AttemptResult<R> observation() {
        return null;
    }

    default LastObservation lastObservation() {
        return null;
    }

    default R result() {
        return Objects.requireNonNull(observation()).result();
    }

    default long completedAttempts() {
        return kind() == Kind.TIMEOUT_BETWEEN_OBSERVATIONS
                ? Objects.requireNonNull(lastObservation()).attempt()
                : Objects.requireNonNull(observation()).attempt();
    }

    record Success<R>(long startedNanos, long acquiredNanos,
            long completedNanos, AttemptResult<R> observation)
            implements WaitResult<R> {

        public Success {
            Objects.requireNonNull(observation);
        }

        @Override
        public Kind kind() {
            return Kind.SUCCESS;
        }
    }

    record TimeoutBetweenAttempts<R>(long startedNanos, long completedNanos,
            LastObservation lastObservation) implements WaitResult<R> {

        public TimeoutBetweenAttempts {
            Objects.requireNonNull(lastObservation);
        }

        @Override
        public Kind kind() {
            return Kind.TIMEOUT_BETWEEN_OBSERVATIONS;
        }
    }

    record LateUnsatisfied<R>(long startedNanos, long completedNanos,
            AttemptResult<R> observation) implements WaitResult<R> {

        public LateUnsatisfied {
            Objects.requireNonNull(observation);
        }

        @Override
        public Kind kind() {
            return Kind.LATE_UNSATISFIED_TIMEOUT;
        }
    }

    record LateSatisfied<R>(long startedNanos, long completedNanos,
            AttemptResult<R> observation) implements WaitResult<R> {

        public LateSatisfied {
            Objects.requireNonNull(observation);
        }

        @Override
        public Kind kind() {
            return Kind.LATE_SATISFIED_TIMEOUT;
        }
    }

    record StabilityLost<R>(long startedNanos, long acquiredNanos,
            long completedNanos, AttemptResult<R> observation)
            implements WaitResult<R> {

        public StabilityLost {
            Objects.requireNonNull(observation);
        }

        @Override
        public Kind kind() {
            return Kind.STABILITY_LOSS;
        }
    }

    record Uncontrolled<R>(AttemptResult<R> observation)
            implements WaitResult<R> {

        public Uncontrolled {
            Objects.requireNonNull(observation);
        }

        @Override
        public Kind kind() {
            return Kind.UNCONTROLLED;
        }
    }

    static <R> WaitResult<R> success(long startedNanos, long acquiredNanos,
            long completedNanos, AttemptResult<R> observation) {
        return new Success<>(startedNanos, acquiredNanos, completedNanos,
                observation);
    }

    static <R> WaitResult<R> timeoutBetween(long startedNanos,
            long completedNanos, LastObservation lastObservation) {
        return new TimeoutBetweenAttempts<>(startedNanos, completedNanos,
                lastObservation);
    }

    static <R> WaitResult<R> lateUnsatisfied(long startedNanos,
            long completedNanos, AttemptResult<R> observation) {
        return new LateUnsatisfied<>(startedNanos, completedNanos, observation);
    }

    static <R> WaitResult<R> lateSatisfied(long startedNanos,
            long completedNanos, AttemptResult<R> observation) {
        return new LateSatisfied<>(startedNanos, completedNanos, observation);
    }

    static <R> WaitResult<R> stabilityLoss(long startedNanos,
            long acquiredNanos, long completedNanos,
            AttemptResult<R> observation) {
        return new StabilityLost<>(startedNanos, acquiredNanos, completedNanos,
                observation);
    }

    static <R> WaitResult<R> uncontrolled(AttemptResult<R> observation) {
        return new Uncontrolled<>(observation);
    }
}
