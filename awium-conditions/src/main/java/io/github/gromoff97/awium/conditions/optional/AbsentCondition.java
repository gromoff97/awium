package io.github.gromoff97.awium.conditions.optional;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Optional;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

record AbsentCondition() implements PreservingCondition<Optional<?>> {
    @Override public String description() { return "optional is absent"; }
    @Override public ConditionResult<?> probe(Optional<?> actual) {
        return actual == null ? unsatisfied("optional was null")
                : actual.isEmpty() ? satisfied(actual) : unsatisfied("optional was present");
    }
}
