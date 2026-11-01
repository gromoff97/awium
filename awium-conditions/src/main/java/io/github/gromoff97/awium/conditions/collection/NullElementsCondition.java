package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.Collection;

import static io.github.gromoff97.awium.support.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.support.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

final class NullElementsCondition extends PreservingCondition<Collection<?>> {
    NullElementsCondition(boolean none, boolean only) {
        super(only ? "collection contains only nulls" : none ? "collection does not contain null" : "collection contains null",
                null, actual -> {
                    if (actual == null) return unsatisfied("collection was null");
                    boolean matches = only ? !actual.isEmpty() && matchesAll(actual, value -> value == null)
                            : matchesAny(actual, value -> value == null) != none;
                    return matches ? satisfied(actual) : unsatisfied(only ? "collection did not contain only nulls"
                            : none ? "collection contained null" : "collection did not contain null");
                });
    }

}
