package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;

import static io.github.gromoff97.awium.internal.DiagnosticText.renderValue;
import static java.util.Objects.requireNonNull;

public interface Condition<INPUT, RESULT> {
    String description();

    default String explanation() { return null; }

    default List<Reference<?>> references() { return List.of(); }

    AttemptResult<RESULT> probe(INPUT actual) throws Throwable;

    default AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception { return this::probe; }

    default Condition<INPUT, RESULT> newRun() { return this; }

    default AttemptResult<?> historyResult(AttemptResult<? extends RESULT> attempt) throws Throwable { return attempt; }

    default int historySlot(Attempt<?> attempt) { return 1; }

    default List<? extends Condition<?, ?>> historyConditions(Condition<?, ?> definition) { return List.of(definition); }

    default Diagnostic diagnostics(Attempt<?> attempt) { return Diagnostic.EMPTY; }

    default Condition<INPUT, RESULT> because(String explanation) {
        return new ExplainedCondition<>(this, explanation);
    }

    record Reference<VALUE>(String label, VALUE value) {
        public Reference {
            requireNonNull(label, "reference label must not be null");
            if (label.isBlank()) throw new IllegalArgumentException("reference label must not be blank");
        }
        @Override public String toString() {
            return label + "=" + renderValue(value);
        }
    }
}
