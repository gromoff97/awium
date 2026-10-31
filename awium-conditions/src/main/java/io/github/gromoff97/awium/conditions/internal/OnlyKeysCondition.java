package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.List;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.internal.ExpectedValues.snapshot;
import static io.github.gromoff97.awium.conditions.internal.ValueMatching.sameDistinctElements;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class OnlyKeysCondition<KEY> extends PreservingCondition<Map<? super KEY, ?>> {
    public OnlyKeysCondition(Iterable<? extends KEY> expected) {
        this(snapshot(expected, "expected keys"));
    }

    private OnlyKeysCondition(List<? extends KEY> keys) {
        super("map contains only the expected keys", new Reference<>("Expected", keys), actual -> actual == null
                ? unsatisfied("map was null") : sameDistinctElements(actual.keySet(), keys)
                        ? satisfied(actual) : unsatisfied("map did not contain only the expected keys"));
    }

}
