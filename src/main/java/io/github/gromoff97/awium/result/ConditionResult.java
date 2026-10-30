package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.internal.DiagnosticText;

/**
 * The two outcomes a condition may return. Thrown failures are handled by the wait engine;
 * they are not condition results.
 */
public sealed interface ConditionResult {

    /** The diagnostic cause, or {@code null} when none was supplied. */
    default Throwable cause() {
        return null;
    }

    static Satisfied satisfied() {
        return new Satisfied();
    }

    static Unsatisfied unsatisfied() {
        return unsatisfied(null, null);
    }

    static Unsatisfied unsatisfied(String mismatch) {
        return unsatisfied(mismatch, null);
    }

    static Unsatisfied unsatisfied(String mismatch, Throwable cause) {
        return new Unsatisfied(mismatch, cause);
    }

    record Satisfied() implements ConditionResult {}

    record Unsatisfied(String mismatch, Throwable cause) implements ConditionResult {
        @Override
        public String toString() {
            return "Unsatisfied[mismatch=" + mismatch + ", cause=" + new DiagnosticText(cause).cause(cause) + "]";
        }
    }
}
