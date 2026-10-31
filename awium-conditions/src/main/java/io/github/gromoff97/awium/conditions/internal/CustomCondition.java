package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

public final class CustomCondition<INPUT, RESULT> extends DefinedCondition<INPUT, RESULT> {
    public CustomCondition(String description,
            CheckedFunction<? super INPUT, ? extends ConditionResult<? extends RESULT>> evaluation) {
        super(description, null, actual -> evaluation.apply(actual));
        requireNonNull(evaluation, "evaluation must not be null");
    }

    public CustomCondition(String description,
            Callable<? extends CheckedFunction<? super INPUT, ? extends ConditionResult<? extends RESULT>>> evaluatorFactory) {
        super(description, null, () -> {
            var evaluator = requireNonNull(evaluatorFactory.call(), "evaluator must not be null");
            return evaluator::apply;
        });
        requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

}
