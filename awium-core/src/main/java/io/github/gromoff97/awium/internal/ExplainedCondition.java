package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;

public final class ExplainedCondition<INPUT, RESULT> extends AwaitCondition.Definition implements Condition<INPUT, RESULT> {
    private final Condition<INPUT, RESULT> original;

    public ExplainedCondition(Condition<INPUT, RESULT> original, String explanation) {
        super(original, explanation);
        this.original = original;
    }

    @Override
    public ConditionResult<RESULT> probe(INPUT actual) throws Throwable { return original.probe(actual); }

    @Override
    public AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception { return original.newAttempt(); }

    @Override
    public ConditionRun<INPUT, RESULT> newRun(AwaitCondition definition, boolean recording) {
        return original.newRun(definition, recording);
    }
}
