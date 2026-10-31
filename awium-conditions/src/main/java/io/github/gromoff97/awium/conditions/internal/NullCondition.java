package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class NullCondition extends PreservingCondition<Object> {
    public NullCondition(boolean expectNull) {
        super(expectNull ? "value is null" : "value is not null", null, actual ->
                (actual == null) == expectNull ? satisfied(actual)
                        : unsatisfied(expectNull ? "value was not null" : "value was null"));
    }

}
