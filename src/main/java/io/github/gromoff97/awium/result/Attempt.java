package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.internal.DiagnosticText;

import static java.util.Objects.requireNonNull;

public sealed interface Attempt<OBSERVED> {

    default Throwable cause() {
        return null;
    }

    record Satisfied<OBSERVED>(OBSERVED observed) implements Attempt<OBSERVED> {
        @Override
        public String toString() {
            return DiagnosticText.attempt(this);
        }
    }

    record Unsatisfied<OBSERVED>(OBSERVED observed, String mismatch, Throwable cause) implements Attempt<OBSERVED> {
        @Override
        public String toString() {
            return DiagnosticText.attempt(this);
        }
    }

    record FailedAfterObservation<OBSERVED>(OBSERVED observed, Operation operation,
            Throwable cause, String location) implements Failure<OBSERVED> {

        public FailedAfterObservation {
            requireNonNull(operation, "operation must not be null");
            requireNonNull(cause, "cause must not be null");
        }

        @Override
        public String toString() {
            return DiagnosticText.attempt(this);
        }
    }

    record FailedBeforeObservation<OBSERVED>(Operation operation, Throwable cause) implements Failure<OBSERVED> {

        public FailedBeforeObservation {
            requireNonNull(operation, "operation must not be null");
            requireNonNull(cause, "cause must not be null");
        }

        @Override
        public String toString() {
            return DiagnosticText.attempt(this);
        }
    }

    sealed interface Failure<OBSERVED> extends Attempt<OBSERVED> {

        Operation operation();

        default String location() {
            return null;
        }

        @Override
        Throwable cause();
    }

    enum Operation {
        WAITING,
        SOURCE,
        CONDITION
    }
}
