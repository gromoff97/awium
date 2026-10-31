package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.internal.DiagnosticText;

public sealed interface ConditionResult<RESULT> {

    default Throwable cause() {
        return null;
    }

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
