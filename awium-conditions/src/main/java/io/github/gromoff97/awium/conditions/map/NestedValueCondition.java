package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;

import static io.github.gromoff97.awium.function.AwaitAttempt.bind;

record NestedValueCondition<INPUT>(Condition<INPUT, ?> nested) implements MapValueCondition<INPUT> {
    @Override public String description() { return nested.description(); }
    @Override public String explanation() { return nested.explanation(); }
    @Override public List<Reference<?>> references() { return nested.references(); }
    @Override public AttemptResult<?> probe(INPUT actual) throws Throwable { return newAttempt().probe(actual); }
    @Override public AwaitAttempt<INPUT, ?> newAttempt() { return bind(nested); }
}
