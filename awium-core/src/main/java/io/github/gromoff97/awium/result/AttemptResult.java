package io.github.gromoff97.awium.result;

import java.util.function.Consumer;
import java.util.function.Function;

import static io.github.gromoff97.awium.internal.DiagnosticText.renderCause;
import static io.github.gromoff97.awium.internal.DiagnosticText.renderValue;
import static java.util.Objects.requireNonNull;

public sealed interface AttemptResult<RESULT> {

    default Throwable cause() {
        return null;
    }

    Diagnostic diagnostic();

    boolean isSatisfied();

    default void ifSatisfied(Consumer<? super RESULT> action) {
        requireNonNull(action, "action must not be null");
    }

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

        @Override public boolean isSatisfied() { return true; }

        @Override public void ifSatisfied(Consumer<? super RESULT> action) {
            requireNonNull(action, "action must not be null").accept(value);
        }

        @Override
        public final String toString() {
            return "Satisfied[value=" + renderValue(value) + ", diagnostic=" + diagnostic + "]";
        }
    }

    record Unsatisfied<RESULT>(Diagnostic diagnostic, Throwable cause) implements AttemptResult<RESULT> {
        public Unsatisfied {
            requireNonNull(diagnostic, "diagnostic must not be null").requireMismatch();
        }
        public String mismatch() { return diagnostic.mismatch(); }

        @Override public boolean isSatisfied() { return false; }

        @Override
        public String toString() {
            return "Unsatisfied[diagnostic=" + diagnostic + ", cause=" + renderCause(cause) + "]";
        }
    }
}
