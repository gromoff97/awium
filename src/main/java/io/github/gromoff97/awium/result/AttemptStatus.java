package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.internal.DiagnosticText;

import static java.util.Objects.requireNonNull;

/** Status and diagnostics of one polling attempt, independent of any extracted value. */
public sealed interface AttemptStatus permits ConditionResult, AttemptStatus.Uncontrolled {

    default Throwable cause() {
        return null;
    }

    /** The condition matched; the wait retains its original observation or selected value. */
    record Satisfied() implements ConditionResult {}

    /** The condition did not match; either diagnostic field may be absent. */
    record Unsatisfied(String mismatch, Throwable cause) implements ConditionResult {
        @Override
        public String toString() {
            return "Unsatisfied[mismatch=" + mismatch + ", cause=" + DiagnosticText.cause(cause) + "]";
        }
    }

    /** The attempt could not complete normally. */
    record Uncontrolled(Operation operation, Throwable cause) implements AttemptStatus {
        public Uncontrolled {
            requireNonNull(operation, "operation must not be null");
            requireNonNull(cause, "cause must not be null");
        }

        @Override
        public String toString() {
            return "Uncontrolled[operation=" + operation + ", cause=" + DiagnosticText.cause(cause) + "]";
        }
    }

    enum Operation { WAITING, SOURCE, CONDITION }
}
