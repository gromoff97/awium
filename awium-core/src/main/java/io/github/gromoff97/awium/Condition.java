package io.github.gromoff97.awium;

import io.github.gromoff97.awium.execution.CheckRun;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;

public interface Condition<INPUT, RESULT> extends AwaitCondition.InputCondition<INPUT> {
    @Override
    AttemptResult<RESULT> probe(INPUT actual) throws Throwable;

    @Override
    default AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception { return this::probe; }

    @Override
    default ConditionRun<INPUT, RESULT> newRun(AwaitCondition definition) {
        return new CheckRun<>(definition, this::newAttempt);
    }

    @Override
    default Condition<INPUT, RESULT> because(String explanation) {
        return new Explained.Typed<>(this, explanation);
    }
}
