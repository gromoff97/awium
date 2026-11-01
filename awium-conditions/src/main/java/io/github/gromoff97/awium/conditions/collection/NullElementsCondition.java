package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Collection;

import static io.github.gromoff97.awium.conditions.collection.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

record NullElementsCondition(Nulls nulls) implements PreservingCondition<Collection<?>> {
    enum Nulls { ANY, NONE, ONLY }

    @Override public String description() { return nulls == Nulls.ONLY ? "collection contains only nulls" : nulls == Nulls.NONE ? "collection does not contain null" : "collection contains null"; }

    @Override
    public ConditionResult<?> probe(Collection<?> actual) {
        if (actual == null) return unsatisfied("collection was null");
        boolean matches = nulls == Nulls.ONLY ? !actual.isEmpty() && matchesAll(actual, value -> value == null)
                : matchesAny(actual, value -> value == null) != (nulls == Nulls.NONE);
        return matches ? satisfied(actual) : unsatisfied(nulls == Nulls.ONLY ? "collection did not contain only nulls"
                : nulls == Nulls.NONE ? "collection contained null" : "collection did not contain null");
    }
}
