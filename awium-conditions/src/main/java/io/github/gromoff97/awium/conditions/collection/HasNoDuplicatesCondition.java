package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.github.gromoff97.awium.conditions.common.internal.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;

final class HasNoDuplicatesCondition extends PreservingCondition<Collection<?>> {
    HasNoDuplicatesCondition() {
        super("collection has no duplicates", null, HasNoDuplicatesCondition::checkUniqueness);
    }

    private static ConditionResult<Collection<?>> checkUniqueness(Collection<?> actual) throws Throwable {
        if (actual == null) return unsatisfied("collection was null");
        List<Object> seen = new ArrayList<>();
        for (Object value : actual) {
            if (matchesAny(seen, previous -> deepEquals(previous, value))) {
                return unsatisfied("duplicate collection element at index " + seen.size());
            }
            seen.add(value);
        }
        return satisfied(actual);
    }

}
