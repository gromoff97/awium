package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.dsl.TypedCondition;
import io.github.gromoff97.awium.result.AttemptResult;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.requireNonNull;

record InstanceOfCondition<RESULT>(Class<RESULT> type, boolean exact) implements TypedCondition<Object, RESULT> {
    InstanceOfCondition { requireNonNull(type, "type must not be null"); }

    @Override
    public String description() {
        if (exact) {
            return "value is exactly an instance of " + type.getTypeName();
        }
        return "value is an instance of " + type.getTypeName();
    }

    @Override
    public AttemptResult<RESULT> probe(Object actual) {
        if (actual == null) {
            return unsatisfied("value was null");
        }
        boolean matches;
        if (exact) {
            matches = actual.getClass() == type;
        } else {
            matches = type.isInstance(actual);
        }
        if (matches) {
            return satisfied(type.cast(actual));
        }
        return unsatisfied("value type was " + actual.getClass().getTypeName());
    }
}
