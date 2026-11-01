package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Map;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record EmptyCondition(boolean expectEmpty) implements PreservingCondition<Map<?, ?>> {
    @Override public String description() { return "map" + (expectEmpty ? " is empty" : " is not empty"); }

    @Override
    public AttemptResult<?> probe(Map<?, ?> actual) {
        return actual == null ? unsatisfied("map was null")
                : actual.isEmpty() == expectEmpty ? satisfied(actual)
                        : unsatisfied("map" + (expectEmpty ? " was not empty" : " was empty"));
    }
}
