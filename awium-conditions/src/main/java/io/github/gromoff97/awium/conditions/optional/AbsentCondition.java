package io.github.gromoff97.awium.conditions.optional;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Optional;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record AbsentCondition() implements PreservingCondition<Optional<?>> {
    @Override public String description() { return "optional is absent"; }
    @Override public AttemptResult<?> probe(Optional<?> actual) {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        if (actual.isEmpty()) {
            return satisfied(actual);
        }
        return unsatisfied("optional was present");
    }
}
