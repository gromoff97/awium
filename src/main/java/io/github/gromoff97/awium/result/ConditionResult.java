package io.github.gromoff97.awium.result;

/** The two outcomes a condition can return; thrown failures are handled by the wait. */
public sealed interface ConditionResult extends AttemptResult
        permits AttemptResult.Satisfied, AttemptResult.Unsatisfied {

    static AttemptResult.Satisfied satisfied() {
        return new AttemptResult.Satisfied();
    }

    static AttemptResult.Unsatisfied unsatisfied() {
        return unsatisfied(null, null);
    }

    static AttemptResult.Unsatisfied unsatisfied(String mismatch) {
        return unsatisfied(mismatch, null);
    }

    static AttemptResult.Unsatisfied unsatisfied(String mismatch, Throwable cause) {
        return new AttemptResult.Unsatisfied(mismatch, cause);
    }
}
