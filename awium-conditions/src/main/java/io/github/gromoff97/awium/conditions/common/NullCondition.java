package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record NullCondition(boolean expectNull) implements PreservingCondition<Object> {
    @Override public String description() { return expectNull ? "value is null" : "value is not null"; }

    @Override
    public AttemptResult<?> probe(Object actual) {
        return (actual == null) == expectNull ? satisfied(actual)
                : unsatisfied(expectNull ? "value was not null" : "value was null");
    }
}
