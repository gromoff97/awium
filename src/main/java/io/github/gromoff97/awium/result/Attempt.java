package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.internal.DiagnosticText;

import static java.util.Objects.requireNonNull;

/**
 * One retained check outcome or execution failure. The variant itself identifies the outcome;
 * the enclosing {@link ConditionHistory} owns the condition and attempt numbers.
 * Observations retain the source value by reference, including legitimate {@code null} values.
 *
 * @param <OBSERVED> the source observation type
 */
public sealed interface Attempt<OBSERVED> {

    /** The original cause, or {@code null} when this attempt has none. */
    default Throwable cause() {
        return null;
    }

    /** The condition accepted the observed value. */
    record Satisfied<OBSERVED>(OBSERVED observed) implements Attempt<OBSERVED> {
        @Override
        public String toString() {
            return DiagnosticText.attempt(this);
        }
    }

    /** The condition rejected the observed value and may be retried, even when it supplies a cause. */
    record Unsatisfied<OBSERVED>(OBSERVED observed, String mismatch, Throwable cause) implements Attempt<OBSERVED> {
        @Override
        public String toString() {
            return DiagnosticText.attempt(this);
        }
    }

    /** Execution failed after the source returned a value, which remains available as {@code observed}. */
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

    /** Execution failed before an observation was available. */
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

    /** Common access to execution failures, which terminate the wait. */
    sealed interface Failure<OBSERVED> extends Attempt<OBSERVED> {

        /** The operation that failed. */
        Operation operation();

        /** The nested condition location, or {@code null} when no location is available. */
        default String location() {
            return null;
        }

        /** The original, non-null failure. */
        @Override
        Throwable cause();
    }

    /** The part of the wait in which execution failed. */
    enum Operation {
        WAITING,
        SOURCE,
        CONDITION
    }
}
