package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Map;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record EmptyCondition(boolean expectEmpty) implements PreservingCondition<Map<?, ?>> {
    @Override
    public String description() {
        if (expectEmpty) {
            return "map is empty";
        }
        return "map is not empty";
    }

    @Override
    public AttemptResult<?> probe(Map<?, ?> actual) {
        if (actual == null) {
            return unsatisfied("map was null");
        }
        if (actual.isEmpty() == expectEmpty) {
            return satisfied(actual);
        }
        if (expectEmpty) {
            return unsatisfied("map was not empty");
        }
        return unsatisfied("map was empty");
    }
}
