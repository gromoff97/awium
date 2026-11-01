package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Collection;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record SingleElementCondition() implements SelectedCondition<Collection<?>> {
    @Override public String description() { return "collection has a single element"; }
    @Override
    public AttemptResult<?> probe(Collection<?> actual) {
        if (actual == null) return unsatisfied("collection was null");
        var iterator = actual.iterator();
        if (!iterator.hasNext()) return unsatisfied("collection size was 0");
        var value = iterator.next();
        return iterator.hasNext() ? unsatisfied("collection size was greater than 1") : satisfied(value);
    }
}
