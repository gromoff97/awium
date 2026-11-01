package io.github.gromoff97.awium.conditions.optional;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Optional;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record PresentCondition() implements SelectedCondition<Optional<?>> {
    @Override public String description() { return "optional is present"; }
    @Override public AttemptResult<?> probe(Optional<?> actual) {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        if (actual.isPresent()) {
            return satisfied(actual.orElseThrow());
        }
        return unsatisfied("optional was empty");
    }
}
