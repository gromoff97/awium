package io.github.gromoff97.awium.conditions.optional;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;

import static io.github.gromoff97.awium.execution.ConditionExecution.bind;

record SelectedNestedCondition<INPUT>(Condition<INPUT, ?> nested) implements SelectedCondition<INPUT> {
    @Override public String description() { return nested.description(); }
    @Override public String explanation() { return nested.explanation(); }
    @Override public List<Reference<?>> references() { return nested.references(); }
    @Override public AttemptResult<?> probe(INPUT actual) throws Throwable { return newAttempt().probe(actual); }
    @Override public AwaitAttempt<INPUT, ?> newAttempt() { return bind(nested)::probe; }
}
