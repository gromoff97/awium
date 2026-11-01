package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.execution.DiagnosticText;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public sealed interface AttemptResult<RESULT> {

    default Throwable cause() {
        return null;
    }

    Diagnostic diagnostic();

    static <RESULT> Satisfied<RESULT> satisfied(RESULT value) {
        return new Satisfied<>(value);
    }

    static <RESULT> Satisfied<RESULT> satisfied(RESULT value, Diagnostic diagnostic) {
        return new Satisfied<>(value, diagnostic);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(String mismatch) {
        return unsatisfied(mismatch, null);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(String mismatch, Throwable cause) {
        return unsatisfied(new Diagnostic(mismatch), cause);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(Diagnostic diagnostic) {
        return unsatisfied(diagnostic, null);
    }

    static <RESULT> Unsatisfied<RESULT> unsatisfied(Diagnostic diagnostic, Throwable cause) {
        return new Unsatisfied<>(diagnostic, cause);
    }

    default <NEXT> AttemptResult<NEXT> mapValue(Function<? super RESULT, ? extends NEXT> mapping) {
        requireNonNull(mapping, "mapping must not be null");
        return switch (this) {
            case Satisfied<RESULT> success -> satisfied(mapping.apply(success.value()), success.diagnostic());
            case Unsatisfied<RESULT> mismatch -> unsatisfied(mismatch.diagnostic(), mismatch.cause());
        };
    }

    record Satisfied<RESULT>(RESULT value, Diagnostic diagnostic) implements AttemptResult<RESULT> {
        public Satisfied { requireNonNull(diagnostic, "diagnostic must not be null"); }
        public Satisfied(RESULT value) { this(value, Diagnostic.EMPTY); }
    }

    record Unsatisfied<RESULT>(Diagnostic diagnostic, Throwable cause) implements AttemptResult<RESULT> {
        public Unsatisfied {
            requireNonNull(diagnostic, "diagnostic must not be null").requireMismatch();
        }
        public String mismatch() { return diagnostic.mismatch(); }

        @Override
        public String toString() {
            return "Unsatisfied[diagnostic=" + diagnostic + ", cause=" + new DiagnosticText(cause).cause(cause) + "]";
        }
    }
}
