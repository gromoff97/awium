package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Collection;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record EmptyCondition(boolean expectEmpty) implements PreservingCondition<Collection<?>> {
    @Override public String description() { return "collection" + (expectEmpty ? " is empty" : " is not empty"); }

    @Override
    public AttemptResult<?> probe(Collection<?> actual) {
        return actual == null ? unsatisfied("collection was null")
                : actual.isEmpty() == expectEmpty ? satisfied(actual)
                        : unsatisfied("collection" + (expectEmpty ? " was not empty" : " was empty"));
    }
}
