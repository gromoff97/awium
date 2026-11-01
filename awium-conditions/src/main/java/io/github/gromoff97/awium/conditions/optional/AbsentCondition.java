package io.github.gromoff97.awium.conditions.optional;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Optional;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record AbsentCondition() implements PreservingCondition<Optional<?>> {
    @Override public String description() { return "optional is absent"; }
    @Override public AttemptResult<?> probe(Optional<?> actual) {
        return actual == null ? unsatisfied("optional was null")
                : actual.isEmpty() ? satisfied(actual) : unsatisfied("optional was present");
    }
}
