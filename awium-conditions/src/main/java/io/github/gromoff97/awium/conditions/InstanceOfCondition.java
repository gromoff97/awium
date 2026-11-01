package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.execution.DefinedCondition;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

final class InstanceOfCondition<RESULT> extends DefinedCondition<Object, RESULT> {
    InstanceOfCondition(Class<RESULT> type, boolean exact) {
        super("value is " + (exact ? "exactly " : "") + "an instance of "
                + requireNonNull(type, "type must not be null").getTypeName(), null, actual ->
                (exact ? actual != null && actual.getClass() == type : type.isInstance(actual))
                        ? satisfied(type.cast(actual))
                        : unsatisfied(actual == null ? "value was null" : "value type was " + actual.getClass().getTypeName()));
    }

}
