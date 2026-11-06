package io.github.gromoff97.awium.result;

import static io.github.gromoff97.awium.internal.DiagnosticReport.attempt;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.SOURCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.WAIT_FAILED;
import static java.util.Objects.requireNonNull;

public sealed interface Attempt<OBSERVED> {

    default Throwable cause() {
        return null;
    }

    default Diagnostic diagnostic() { return Diagnostic.EMPTY; }

    default Operation operation() { return null; }

    record SatisfiedObservation<OBSERVED>(OBSERVED observed, Diagnostic diagnostic) implements Observation<OBSERVED> {
        public SatisfiedObservation { requireNonNull(diagnostic, "diagnostic must not be null"); }
        public SatisfiedObservation(OBSERVED observed) { this(observed, Diagnostic.EMPTY); }
        @Override
        public String toString() {
            return attempt(this);
        }
    }

    record UnsatisfiedObservation<OBSERVED>(OBSERVED observed, Diagnostic diagnostic, Throwable cause) implements Observation<OBSERVED> {
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
            Throwable cause, String location, Diagnostic diagnostic) implements Observation<OBSERVED>, Failure<OBSERVED> {

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

    sealed interface Observation<OBSERVED> extends Attempt<OBSERVED> {

        OBSERVED observed();
    }

    sealed interface Failure<OBSERVED> extends Attempt<OBSERVED> {

        @Override
        Operation operation();

        default String location() {
            return null;
        }

        @Override
        Throwable cause();
    }

    enum Operation {
        WAITING(WAIT_FAILED),
        SOURCE(SOURCE_FAILED),
        CONDITION(CONDITION_FAILED);

        private final Reason failureReason;

        Operation(Reason failureReason) { this.failureReason = failureReason; }

        public Reason failureReason() { return failureReason; }
    }
}
