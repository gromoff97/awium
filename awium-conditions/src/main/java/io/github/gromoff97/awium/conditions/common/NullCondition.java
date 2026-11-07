package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record NullCondition(boolean expectNull) implements PreservingCondition<Object> {
    @Override
    public String description() {
        if (expectNull) {
            return "value is null";
        }
        return "value is not null";
    }

    @Override
    public AttemptResult<?> probe(Object actual) {
        if ((actual == null) == expectNull) {
            return satisfied(actual);
        }
        if (expectNull) {
            return unsatisfied("value was not null");
        }
        return unsatisfied("value was null");
    }
}
