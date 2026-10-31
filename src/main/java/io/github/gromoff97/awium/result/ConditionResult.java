package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.internal.DiagnosticText;

/**
 * The two outcomes a condition may return: a successful value or a mismatch to retry.
 * Thrown failures are handled by the wait engine; they are not condition results.
 */
public sealed interface ConditionResult<RESULT> {

    /** The diagnostic cause, or {@code null} when none was supplied. */
    default Throwable cause() {
        return null;
    }

    /** Completes the check with the given value, which may be {@code null}. */
    static <RESULT> Satisfied<RESULT> satisfied(RESULT value) {
        return new Satisfied<>(value);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied() {
        return unsatisfied(null, null);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(String mismatch) {
        return unsatisfied(mismatch, null);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(String mismatch, Throwable cause) {
        return new Unsatisfied<>(mismatch, cause);
    }

    record Satisfied<RESULT>(RESULT value) implements ConditionResult<RESULT> {}

    record Unsatisfied<RESULT>(String mismatch, Throwable cause) implements ConditionResult<RESULT> {
        @Override
        public String toString() {
            return "Unsatisfied[mismatch=" + mismatch + ", cause=" + new DiagnosticText(cause).cause(cause) + "]";
        }
    }
}
