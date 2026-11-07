package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;

import static java.util.Objects.requireNonNull;

record ExplainedCondition<INPUT, RESULT>(Condition<INPUT, RESULT> original,
        String explanation) implements Condition<INPUT, RESULT> {
    ExplainedCondition {
        requireNonNull(original, "condition must not be null");
        requireNonNull(explanation, "explanation must not be null");
        if (explanation.isBlank()) throw new IllegalArgumentException("explanation must not be blank");
    }
    @Override public String description() { return original.description(); }
    @Override public List<Reference<?>> references() { return original.references(); }
    @Override public AttemptResult<RESULT> probe(INPUT actual) throws Throwable { return original.probe(actual); }
    @Override public AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception { return original.newAttempt(); }
    @Override public Condition<INPUT, RESULT> newRun() { return original.newRun(); }
    @Override public AttemptResult<?> historyResult(AttemptResult<? extends RESULT> result) throws Throwable { return original.historyResult(result); }
    @Override public int historySlot(Attempt<?> attempt) { return original.historySlot(attempt); }
    @Override public List<? extends Condition<?, ?>> historyConditions(Condition<?, ?> definition) { return original.historyConditions(definition); }
    @Override public Diagnostic diagnostics(Attempt<?> attempt) { return original.diagnostics(attempt); }
    @Override public Condition<INPUT, RESULT> because(String reason) { return new ExplainedCondition<>(original, reason); }
}
