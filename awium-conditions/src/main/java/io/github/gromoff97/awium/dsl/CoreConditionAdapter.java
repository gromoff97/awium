package io.github.gromoff97.awium.dsl;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;

import static java.util.Objects.requireNonNull;

record CoreConditionAdapter<INPUT, RESULT>(Condition<? super INPUT, RESULT> original) implements TypedCondition<INPUT, RESULT> {
    CoreConditionAdapter { requireNonNull(original, "condition must not be null"); }
    @Override public TypedCondition<INPUT, RESULT> because(String reason) {
        return new CoreConditionAdapter<>(original.because(reason));
    }
    @Override public String description() { return original.description(); }
    @Override public String explanation() { return original.explanation(); }
    @Override public List<Reference<?>> references() { return original.references(); }
    @Override public AttemptResult<RESULT> probe(INPUT actual) throws Throwable { return original.probe(actual); }
    @Override public AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception { return original.newAttempt(); }
    @Override public TypedCondition<INPUT, RESULT> newRun() { return new CoreConditionAdapter<>(original.newRun()); }
    @Override public AttemptResult<?> historyResult(AttemptResult<? extends RESULT> attempt) throws Throwable { return original.historyResult(attempt); }
    @Override public int historySlot(Attempt<?> attempt) { return original.historySlot(attempt); }
    @Override public List<? extends Condition<?, ?>> historyConditions(Condition<?, ?> definition) { return original.historyConditions(definition); }
    @Override public Diagnostic diagnostics(Attempt<?> attempt) { return original.diagnostics(attempt); }
}
