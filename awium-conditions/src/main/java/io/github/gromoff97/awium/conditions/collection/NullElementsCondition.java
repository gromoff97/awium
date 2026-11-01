package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Collection;

import static io.github.gromoff97.awium.conditions.collection.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record NullElementsCondition(Nulls nulls) implements PreservingCondition<Collection<?>> {
    enum Nulls { ANY, NONE, ONLY }

    @Override
    public String description() {
        if (nulls == Nulls.ONLY) {
            return "collection contains only nulls";
        }
        if (nulls == Nulls.NONE) {
            return "collection does not contain null";
        }
        return "collection contains null";
    }

    @Override
    public AttemptResult<?> probe(Collection<?> actual) {
        if (actual == null) return unsatisfied("collection was null");
        boolean matches;
        if (nulls == Nulls.ONLY) {
            matches = !actual.isEmpty() && matchesAll(actual, value -> value == null);
        } else {
            matches = matchesAny(actual, value -> value == null) != (nulls == Nulls.NONE);
        }
        if (matches) {
            return satisfied(actual);
        }
        if (nulls == Nulls.ONLY) {
            return unsatisfied("collection did not contain only nulls");
        }
        if (nulls == Nulls.NONE) {
            return unsatisfied("collection contained null");
        }
        return unsatisfied("collection did not contain null");
    }
}
