package io.github.gromoff97.awium.result;

import static java.util.Objects.requireNonNull;

/** Status and diagnostics of one polling attempt, independent of any extracted value. */
public sealed interface AttemptStatus permits ConditionResult, AttemptStatus.Uncontrolled {

    default Throwable cause() {
        return null;
    }

    /** The condition matched; the wait retains its original observation or selected value. */
    record Satisfied() implements ConditionResult {}

    /** The condition did not match; either diagnostic field may be absent. */
    record Unsatisfied(String mismatch, Throwable cause) implements ConditionResult {}

    /** The attempt could not complete normally. */
    record Uncontrolled(Operation operation, Throwable cause) implements AttemptStatus {
        public Uncontrolled {
            requireNonNull(operation, "operation must not be null");
            requireNonNull(cause, "cause must not be null");
        }
    }

    enum Operation { WAITING, SOURCE, CONDITION }
}
