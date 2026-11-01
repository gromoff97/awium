package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;

public interface Condition<INPUT, RESULT> extends AwaitCondition.InputCondition<INPUT> {
    @Override
    AttemptResult<RESULT> probe(INPUT actual) throws Throwable;

    @Override
    default AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception { return this::probe; }

    @Override
    default Condition<INPUT, RESULT> newRun() { return this; }

    default AttemptResult<?> historyResult(AttemptResult<? extends RESULT> attempt) throws Throwable { return attempt; }

    default int historySlot(Attempt<?> attempt) { return 1; }

    default List<? extends AwaitCondition> historyConditions(AwaitCondition definition) { return List.of(definition); }

    default Diagnostic diagnostics(Attempt<?> attempt) { return Diagnostic.EMPTY; }

    @Override
    default Condition<INPUT, RESULT> because(String explanation) {
        return new Explained.Typed<>(this, explanation);
    }
}
