package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Collection;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record EmptyCondition(boolean expectEmpty) implements PreservingCondition<Collection<?>> {
    @Override
    public String description() {
        if (expectEmpty) {
            return "collection is empty";
        }
        return "collection is not empty";
    }

    @Override
    public AttemptResult<?> probe(Collection<?> actual) {
        if (actual == null) {
            return unsatisfied("collection was null");
        }
        if (actual.isEmpty() == expectEmpty) {
            return satisfied(actual);
        }
        if (expectEmpty) {
            return unsatisfied("collection was not empty");
        }
        return unsatisfied("collection was empty");
    }
}
