package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.result.AttemptResult.Satisfied;

public final class CheckRun<OBSERVED, RESULT> extends ConditionRun<OBSERVED, RESULT> {

    private final ConditionExecution<OBSERVED, RESULT> evaluation;
    private AttemptResult<? extends RESULT> verdict;

    public CheckRun(AwaitCondition condition,
            Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
        super(condition);
        evaluation = new ConditionExecution<>(evaluatorFactory);
    }

    @Override
    public AttemptResult<? extends RESULT> probe(OBSERVED actual) throws Throwable {
        verdict = null;
        verdict = evaluation.probe(actual);
        return verdict;
    }

    @Override
    public boolean complete() { return verdict instanceof Satisfied<?>; }

    @Override
    public RESULT result() { return ((Satisfied<? extends RESULT>) verdict).value(); }
}
