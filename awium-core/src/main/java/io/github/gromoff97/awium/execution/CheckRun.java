package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;

public final class CheckRun<OBSERVED, RESULT> extends AbstractConditionRun<OBSERVED, RESULT> {

    private final ConditionExecution<OBSERVED, RESULT> evaluation;
    private ConditionResult<? extends RESULT> verdict;

    public CheckRun(AwaitCondition condition,
            Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
        super(condition);
        evaluation = new ConditionExecution<>(evaluatorFactory);
    }

    @Override
    public ConditionResult<? extends RESULT> probe(OBSERVED actual) throws Throwable {
        verdict = null;
        verdict = evaluation.probe(actual);
        return verdict;
    }

    @Override
    public boolean complete() { return verdict instanceof Satisfied<?>; }

    @Override
    public RESULT result() { return ((Satisfied<? extends RESULT>) verdict).value(); }
}
