package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.ConditionResult;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

record InstanceOfCondition<RESULT>(Class<RESULT> type, boolean exact) implements Condition<Object, RESULT> {
    InstanceOfCondition { requireNonNull(type, "type must not be null"); }

    @Override public String description() { return "value is " + (exact ? "exactly " : "") + "an instance of " + type.getTypeName(); }

    @Override
    public ConditionResult<RESULT> probe(Object actual) {
        return (exact ? actual != null && actual.getClass() == type : type.isInstance(actual))
                ? satisfied(type.cast(actual))
                : unsatisfied(actual == null ? "value was null" : "value type was " + actual.getClass().getTypeName());
    }
}
