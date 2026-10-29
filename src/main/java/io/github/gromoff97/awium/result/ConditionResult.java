package io.github.gromoff97.awium.result;

/** The two outcomes a condition can return; thrown failures are handled by the wait. */
public sealed interface ConditionResult extends AttemptStatus
        permits AttemptStatus.Satisfied, AttemptStatus.Unsatisfied {

    static AttemptStatus.Satisfied satisfied() {
        return new AttemptStatus.Satisfied();
    }

    static AttemptStatus.Unsatisfied unsatisfied() {
        return unsatisfied(null, null);
    }

    static AttemptStatus.Unsatisfied unsatisfied(String mismatch) {
        return unsatisfied(mismatch, null);
    }

    static AttemptStatus.Unsatisfied unsatisfied(String mismatch, Throwable cause) {
        return new AttemptStatus.Unsatisfied(mismatch, cause);
    }
}
