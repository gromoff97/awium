package io.github.gromoff97.awium.result;

import static io.github.gromoff97.awium.execution.DiagnosticText.attempt;
import static java.util.Objects.requireNonNull;

public sealed interface Attempt<OBSERVED> {

    default Throwable cause() {
        return null;
    }

    default Diagnostic diagnostic() { return Diagnostic.EMPTY; }

    record SatisfiedObservation<OBSERVED>(OBSERVED observed, Diagnostic diagnostic) implements Attempt<OBSERVED> {
        public SatisfiedObservation { requireNonNull(diagnostic, "diagnostic must not be null"); }
        public SatisfiedObservation(OBSERVED observed) { this(observed, Diagnostic.EMPTY); }
        @Override
        public String toString() {
            return attempt(this);
        }
    }

    record UnsatisfiedObservation<OBSERVED>(OBSERVED observed, Diagnostic diagnostic, Throwable cause) implements Attempt<OBSERVED> {
        public UnsatisfiedObservation {
            requireNonNull(diagnostic, "diagnostic must not be null").requireMismatch();
        }
        public UnsatisfiedObservation(OBSERVED observed, String mismatch, Throwable cause) {
            this(observed, new Diagnostic(mismatch), cause);
        }
        public String mismatch() { return diagnostic.mismatch(); }
        @Override
        public String toString() {
            return attempt(this);
        }
    }

    record FailedAfterObservation<OBSERVED>(OBSERVED observed, Operation operation,
            Throwable cause, String location, Diagnostic diagnostic) implements Failure<OBSERVED> {

        public FailedAfterObservation {
            requireNonNull(operation, "operation must not be null");
            requireNonNull(cause, "cause must not be null");
            requireNonNull(diagnostic, "diagnostic must not be null");
        }

        public FailedAfterObservation(OBSERVED observed, Operation operation, Throwable cause, String location) {
            this(observed, operation, cause, location, Diagnostic.EMPTY);
        }

        @Override
        public String toString() {
            return attempt(this);
        }
    }

    record FailedBeforeObservation<OBSERVED>(Operation operation, Throwable cause) implements Failure<OBSERVED> {

        public FailedBeforeObservation {
            requireNonNull(operation, "operation must not be null");
            requireNonNull(cause, "cause must not be null");
        }

        @Override
        public String toString() {
            return attempt(this);
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
