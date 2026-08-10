package io.github.gromoff97.awium.internal.engine;

import java.util.Objects;

public sealed interface AttemptResult<R>
        permits AttemptResult.Satisfied, AttemptResult.Unsatisfied,
                AttemptResult.Uncontrolled {

    enum Status { SATISFIED, UNSATISFIED, UNCONTROLLED }

    enum Origin { WAITING, SOURCE, CONDITION }

    Status status();

    boolean hasActual();

    Object actual();

    long attempt();

    default R result() {
        return null;
    }

    default String mismatch() {
        return null;
    }

    default AssertionError assertionCause() {
        return null;
    }

    default Origin origin() {
        return null;
    }

    default Throwable cause() {
        return null;
    }

    default boolean isSatisfied() {
        return status() == Status.SATISFIED;
    }

    default boolean isUnsatisfied() {
        return status() == Status.UNSATISFIED;
    }

    default boolean isUncontrolled() {
        return status() == Status.UNCONTROLLED;
    }

    record Satisfied<R>(Object actual, R result, long attempt)
            implements AttemptResult<R> {

        @Override
        public Status status() {
            return Status.SATISFIED;
        }

        @Override
        public boolean hasActual() {
            return true;
        }
    }

    record Unsatisfied<R>(Object actual, String mismatch,
            AssertionError assertionCause, long attempt)
            implements AttemptResult<R> {

        public Unsatisfied {
            Objects.requireNonNull(mismatch);
        }

        @Override
        public Status status() {
            return Status.UNSATISFIED;
        }

        @Override
        public boolean hasActual() {
            return true;
        }
    }

    record Uncontrolled<R>(Origin origin, boolean hasActual, Object actual,
            Throwable cause, long attempt) implements AttemptResult<R> {

        public Uncontrolled {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(cause);
        }

        @Override
        public Status status() {
            return Status.UNCONTROLLED;
        }
    }

    static <R> AttemptResult<R> satisfied(
            Object actual, R result, long attempt) {
        return new Satisfied<>(actual, result, attempt);
    }

    static <R> AttemptResult<R> unsatisfied(
            Object actual, String mismatch, AssertionError assertionCause,
            long attempt) {
        return new Unsatisfied<>(actual, mismatch, assertionCause, attempt);
    }

    static <R> AttemptResult<R> uncontrolled(
            Origin origin, Throwable cause, long attempt) {
        return new Uncontrolled<>(origin, false, null, cause, attempt);
    }

    static <R> AttemptResult<R> uncontrolled(
            Origin origin, Throwable cause, long attempt, Object actual) {
        return new Uncontrolled<>(origin, true, actual, cause, attempt);
    }
}
