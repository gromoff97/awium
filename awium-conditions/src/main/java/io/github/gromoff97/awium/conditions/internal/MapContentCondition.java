package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.internal.MapEntries.snapshotEntries;
import static io.github.gromoff97.awium.conditions.internal.ValueMatching.sameElementsInAnyOrder;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class MapContentCondition<INPUT extends Map<?, ?>> extends PreservingCondition<INPUT> {
    public MapContentCondition(Map<?, ?> expected, boolean negated) {
        this(snapshotEntries(expected, negated ? "unexpected entries" : "expected entries"), negated);
    }

    private MapContentCondition(List<? extends Entry<?, ?>> expected, boolean negated) {
        super(negated ? "map does not contain exactly the unexpected entries" : "map contains exactly the expected entries",
                new Reference<>(negated ? "Unexpected" : "Expected", expected), actual -> actual == null
                        ? unsatisfied("map was null") : exactContent(actual, expected) != negated
                                ? satisfied(actual) : unsatisfied(negated ? "map contained exactly the unexpected entries"
                                        : "map did not contain exactly the expected entries"));
    }

    private static boolean exactContent(Map<?, ?> actual, List<? extends Entry<?, ?>> expected) throws Throwable {
        return actual.size() == expected.size()
                && sameElementsInAnyOrder(actual.entrySet().iterator(), expected, MapEntries::entryMatches);
    }

}
