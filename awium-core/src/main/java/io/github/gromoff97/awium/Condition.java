package io.github.gromoff97.awium;

import io.github.gromoff97.awium.execution.CheckRun;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;

public interface Condition<INPUT, RESULT> extends AwaitCondition.InputCondition<INPUT> {
    @Override
    ConditionResult<RESULT> probe(INPUT actual) throws Throwable;

    @Override
    default AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception { return this::probe; }

    @Override
    default ConditionRun<INPUT, RESULT> newRun(AwaitCondition definition, boolean recording) {
        return new CheckRun<>(definition, this::newAttempt, recording);
    }

    @Override
    default Condition<INPUT, RESULT> because(String explanation) {
        return new Explained.Typed<>(this, explanation);
    }
}
